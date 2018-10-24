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
import com.torodb.core.bundle.BundleConfig;
import com.torodb.core.logging.LoggerFactory;
import com.torodb.core.metrics.ToroMetricRegistry;
import com.torodb.mongodb.core.MongoDbCoreBundle;
import com.torodb.mongodb.repl.filters.ReplicationFilters;
import com.torodb.mongodb.repl.oplogreplier.offheapbuffer.OffHeapBufferConfig;
import com.torodb.mongowp.client.wrapper.MongoClientConfigurationProperties;

import java.util.Optional;

public class MongoDbReplConfigBuilder {

  private final BundleConfig generalConfig;
  private MongoDbCoreBundle coreBundle;
  private ImmutableList<HostAndPort> seeds;
  private MongoClientConfigurationProperties mongoClientConfigurationProperties;
  private ReplicationFilters replicationFilters;
  private String replSetName;
  private ConsistencyHandler consistencyHandler;
  private Optional<ToroMetricRegistry> metricRegistry;
  private LoggerFactory loggerFactory;
  private OffHeapBufferConfig offHeapBufferConfig;

  public MongoDbReplConfigBuilder(BundleConfig generalConfig) {
    this.generalConfig = generalConfig;
  }

  public MongoDbReplConfigBuilder setCoreBundle(MongoDbCoreBundle coreBundle) {
    this.coreBundle = coreBundle;
    return this;
  }

  public MongoDbReplConfigBuilder setSeeds(ImmutableList<HostAndPort> seeds) {
    this.seeds = seeds;
    return this;
  }

  public MongoDbReplConfigBuilder setMongoClientConfigurationProperties(
      MongoClientConfigurationProperties mongoClientConfigurationProperties) {
    this.mongoClientConfigurationProperties = mongoClientConfigurationProperties;
    return this;
  }

  public MongoDbReplConfigBuilder setReplicationFilters(ReplicationFilters replicationFilters) {
    this.replicationFilters = replicationFilters;
    return this;
  }

  public MongoDbReplConfigBuilder setReplSetName(String replSetName) {
    this.replSetName = replSetName;
    return this;
  }

  public MongoDbReplConfigBuilder setConsistencyHandler(ConsistencyHandler consistencyHandler) {
    this.consistencyHandler = consistencyHandler;
    return this;
  }

  public MongoDbReplConfigBuilder setMetricRegistry(Optional<ToroMetricRegistry> metricRegistry) {
    this.metricRegistry = metricRegistry;
    return this;
  }

  public MongoDbReplConfigBuilder setLoggerFactory(LoggerFactory loggerFactory) {
    this.loggerFactory = loggerFactory;
    return this;
  }

  public MongoDbReplConfigBuilder setOffHeapBufferConfig(
      OffHeapBufferConfig offHeapBufferConfig) {
    this.offHeapBufferConfig = offHeapBufferConfig;
    return this;
  }

  public MongoDbReplConfig build() {
    Preconditions.checkNotNull(coreBundle, "core bundle must be not null");
    Preconditions.checkNotNull(seeds, "seeds must be not null");
    Preconditions.checkNotNull(mongoClientConfigurationProperties, "mongo client configuration"
        + " properties must be not null");
    Preconditions.checkNotNull(replicationFilters, "replication filters must be not null");
    Preconditions.checkNotNull(replSetName, "replSetName must be not null");
    Preconditions.checkNotNull(consistencyHandler, "consistency handler must be not null");
    Preconditions.checkNotNull(generalConfig, "general config must be not null");
    Preconditions.checkNotNull(metricRegistry, "metric registry must be not null");
    Preconditions.checkNotNull(loggerFactory, "logger factory must be not null");
    Preconditions.checkNotNull(offHeapBufferConfig, "off heap buffer config must be not null");

    return new MongoDbReplConfig(coreBundle, seeds,
        mongoClientConfigurationProperties, replicationFilters,
        replSetName, consistencyHandler, metricRegistry, loggerFactory, generalConfig,
        offHeapBufferConfig);
  }

}
