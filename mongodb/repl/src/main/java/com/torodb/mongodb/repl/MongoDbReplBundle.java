/*
 * ToroDB
 * Copyright © 2014 8Kdata Technology (www.8kdata.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.torodb.mongodb.repl;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.torodb.core.bundle.AbstractBundle;
import com.torodb.core.bundle.BundleConfig;
import com.torodb.core.bundle.BundleConfigImpl;
import com.torodb.core.supervision.Supervisor;
import com.torodb.core.supervision.SupervisorDecision;
import com.torodb.mongodb.core.MongodServer;
import com.torodb.mongodb.filters.DatabaseFilter;
import com.torodb.mongodb.filters.IndexFilter;
import com.torodb.mongodb.filters.NamespaceFilter;
import com.torodb.mongodb.repl.commands.ReplCommandsBuilder;
import com.torodb.mongodb.repl.filters.ToroDbReplicationFilters;
import com.torodb.mongodb.repl.guice.MongoDbRepl;
import com.torodb.mongodb.repl.guice.MongoDbReplModule;
import com.torodb.mongodb.repl.guice.OplogApplierServiceModule;
import com.torodb.mongodb.repl.guice.ReplEssentialOverrideModule;
import com.torodb.mongodb.repl.guice.ReplSetName;
import com.torodb.mongodb.repl.oplogreplier.DefaultOplogApplierBundle;
import com.torodb.mongodb.repl.oplogreplier.DefaultOplogApplierBundleConfig;
import com.torodb.mongodb.repl.oplogreplier.OplogApplier;
import com.torodb.mongodb.repl.topology.RemoteSeed;
import com.torodb.mongodb.repl.topology.TopologyBundle;
import com.torodb.mongodb.repl.topology.TopologyBundleConfig;
import com.torodb.mongodb.utils.DbCloner;
import com.torodb.mongowp.client.core.MongoClientFactory;
import com.torodb.mongowp.client.wrapper.MongoClientConfigurationProperties;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.Collections;

/**
 * MongoDbReplBundle is a aggregation bundle that starts all replication submodules.
 * 
 * When the bundle start, it tries to the node it has to calculate a sync source from by retriving
 * the topology from the given {@link MongoDbReplConfig#getSyncSourceSeed() sync source seed} and
 * then it tries to replicate from.
 */
public class MongoDbReplBundle extends AbstractBundle<MongoDbReplExtInt> {

  private final Logger logger;
  private final BundleConfigImpl replBundleConfig;
  private final MongoDbReplConfig config;
  private final ReplCoordinator replCoordinator;
  private final ReplCoreBundle replCoreBundle;
  private final TopologyBundle topologyBundle;
  private final DefaultOplogApplierBundle oplogApplierBundle;
  private final DbCloner dbCloner;
  private final ToroDbReplicationFilters toroDbReplicationFilters;

  @SuppressWarnings("checkstyle:JavadocMethod")
  public MongoDbReplBundle(MongoDbReplConfig config) {
    super(config);
    this.logger = config.getLoggerFactory().apply(this.getClass());
    this.config = config;
    this.toroDbReplicationFilters = new ToroDbReplicationFilters(config.getUserReplicationFilter());

    replBundleConfig = new BundleConfigImpl(
        config.getEssentialInjector(),
        new ReplSupervisor(config.getSupervisor())
    );

    ReplEssentialOverrideModule essentialOverrideModule = new ReplEssentialOverrideModule(
        config.getMetricRegistry(),
        config.getLoggerFactory()
    );

    replCoreBundle = new ReplCoreBundle(
        createReplCoreConfig(replBundleConfig, essentialOverrideModule)
    );
    topologyBundle = new TopologyBundle(
        createTopologyBundleConfig(replBundleConfig, replCoreBundle, essentialOverrideModule)
    );
    oplogApplierBundle = new DefaultOplogApplierBundle(
        createOplogApplierServiceBundleConfig(
            replBundleConfig,
            replCoreBundle,
            essentialOverrideModule
        )
    );

    Injector replInjector = config.getEssentialInjector().createChildInjector(
        essentialOverrideModule,
        new HubModule(),
        new MongoDbReplModule(),
        new OplogApplierServiceModule()
    );
    this.replCoordinator = replInjector.getInstance(ReplCoordinator.class);
    this.dbCloner = replInjector.getInstance(Key.get(DbCloner.class, MongoDbRepl.class));
  }

  @Override
  protected void postDependenciesStartUp() throws Exception {
    logger.info("Starting replication service");

    replCoreBundle.startAsync();
    replCoreBundle.awaitRunning();

    topologyBundle.startAsync();
    topologyBundle.awaitRunning();

    oplogApplierBundle.startAsync();
    oplogApplierBundle.awaitRunning();

    dbCloner.startAsync();
    dbCloner.awaitRunning();

    replCoordinator.startAsync();
    replCoordinator.awaitRunning();

    logger.info("Replication service started");
  }

  @Override
  protected void preDependenciesShutDown() throws Exception {
    logger.info("Shutting down replication service");

    try {
      replCoordinator.stopAsync();
      replCoordinator.awaitTerminated();
    } catch (IllegalStateException ex) {
      Preconditions.checkState(!replCoordinator.isRunning(),
          "It was expected that {} was not running", replCoordinator);
    }

    dbCloner.stopAsync();
    dbCloner.awaitTerminated();

    oplogApplierBundle.stopAsync();
    oplogApplierBundle.awaitTerminated();

    topologyBundle.stopAsync();
    topologyBundle.awaitTerminated();

    replCoreBundle.stopAsync();
    replCoreBundle.awaitTerminated();

    logger.info("Replication service shutted down");
  }

  @Override
  public Collection<Service> getDependencies() {
    return Collections.singleton(config.getMongoDbCoreBundle());
  }

  @Override
  public MongoDbReplExtInt getExternalInterface() {
    return new MongoDbReplExtInt();
  }

  private ReplCoreConfig createReplCoreConfig(BundleConfig replBundleConfig,
      ReplEssentialOverrideModule essentialOverrideModule) {
    return new ReplCoreConfig(
        config.getMongoClientConfigurationProperties(),
        toroDbReplicationFilters,
        config.getMongoDbCoreBundle(),
        essentialOverrideModule,
        config.getEssentialInjector(),
        replBundleConfig.getSupervisor()
    );
  }

  private TopologyBundleConfig createTopologyBundleConfig(BundleConfig replBundleConfig,
      ReplCoreBundle replCoreBundle, ReplEssentialOverrideModule essentialOverrideModule) {
    return new TopologyBundleConfig(
        replCoreBundle.getExternalInterface().getMongoClientFactory(),
        config.getReplSetName(),
        config.getSeeds(),
        essentialOverrideModule,
        replBundleConfig
    );
  }

  private DefaultOplogApplierBundleConfig createOplogApplierServiceBundleConfig(
      BundleConfig replBundleConfig, ReplCoreBundle replCoreBundle,
      ReplEssentialOverrideModule essentialOverrideModule) {
    
    ReplCommandsBuilder replCommandsBuilder = new ReplCommandsBuilder(
        replBundleConfig,
        toroDbReplicationFilters,
        essentialOverrideModule
    );

    return new DefaultOplogApplierBundleConfig(
        replCoreBundle,
        config.getMongoDbCoreBundle(),
        replCommandsBuilder.getReplCommandsLibrary(),
        replCommandsBuilder.getReplCommandsExecutor(),
        essentialOverrideModule,
        replBundleConfig,
        config.getOffHeapBufferConfig()
    );
  }

  private class ReplSupervisor implements Supervisor {

    private final Supervisor supervisor;

    public ReplSupervisor(Supervisor supervisor) {
      this.supervisor = supervisor;
    }

    @Override
    public SupervisorDecision onError(Object supervised, Throwable error) {
      logger.error("Catched an error on the replication layer. Escalating it");
      SupervisorDecision decision = supervisor.onError(this, error);
      if (decision == SupervisorDecision.STOP) {
        MongoDbReplBundle.this.stopAsync();
      }
      return decision;
    }

    @Override
    public String toString() {
      return "replication supervisor";
    }
  }

  private class HubModule extends AbstractModule {

    @Override
    protected void configure() {
      bindFilters();

      bindConfig();

      bindReplCoreBundle();

      bind(OplogApplier.class)
          .toInstance(oplogApplierBundle.getExternalInterface().getOplogApplier());
      bind(SyncSourceProvider.class)
          .toInstance(topologyBundle.getExternalInterface());
    }

    private void bindFilters() {
      bind(ToroDbReplicationFilters.class)
          .toInstance(toroDbReplicationFilters);
      bind(DatabaseFilter.class)
          .toInstance(toroDbReplicationFilters.getDatabaseFilter());
      bind(NamespaceFilter.class)
          .toInstance(toroDbReplicationFilters.getNamespaceFilter());
      bind(IndexFilter.class)
          .toInstance(toroDbReplicationFilters.getIndexFilter());
    }

    private void bindReplCoreBundle() {
      ReplCoreExtInt extInt = replCoreBundle.getExternalInterface();
      bind(MongoClientFactory.class)
          .toInstance(extInt.getMongoClientFactory());
      bind(OplogReaderProvider.class)
          .toInstance(extInt.getOplogReaderProvider());
      bind(ReplMetrics.class)
          .toInstance(extInt.getReplMetrics());
      bind(OplogManager.class)
          .toInstance(extInt.getOplogManager());
    }

    private void bindConfig() {
      bind(MongodServer.class)
          .toInstance(config.getMongoDbCoreBundle().getExternalInterface().getMongodServer());
      bind(ConsistencyHandler.class)
          .toInstance(config.getConsistencyHandler());
      bind(MongoClientConfigurationProperties.class)
          .toInstance(config.getMongoClientConfigurationProperties());
      bind(String.class)
          .annotatedWith(ReplSetName.class)
          .toInstance(config.getReplSetName());
      bind(new TypeLiteral<ImmutableList<HostAndPort>>() {})
        .annotatedWith(RemoteSeed.class)
        .toInstance(config.getSeeds());
    }

    @Provides
    @MongoDbRepl
    Supervisor getReplSupervisor() {
      return replBundleConfig.getSupervisor();
    }

  }
}
