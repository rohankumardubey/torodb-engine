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

package com.torodb.mongodb.repl.topology;

import com.google.common.collect.ImmutableList;
import com.google.common.net.HostAndPort;
import com.torodb.core.bundle.BundleConfig;
import com.torodb.mongodb.repl.guice.ReplEssentialOverrideModule;
import com.torodb.mongodb.repl.impl.FollowerSyncSourceProviderConfig;
import com.torodb.mongowp.client.core.MongoClientFactory;

public class TopologyBundleConfig extends FollowerSyncSourceProviderConfig {

  private final MongoClientFactory clientFactory;
  private final String replSetName;
  private final ReplEssentialOverrideModule replEssentialOverrideModule;

  public TopologyBundleConfig(MongoClientFactory clientFactory, String replSetName,
      ImmutableList<HostAndPort> seeds, ReplEssentialOverrideModule replEssentialOverrideModule,
      BundleConfig delegate) {
    super(seeds, delegate);
    this.clientFactory = clientFactory;
    this.replSetName = replSetName;
    this.replEssentialOverrideModule = replEssentialOverrideModule;
  }

  public MongoClientFactory getClientFactory() {
    return clientFactory;
  }

  public String getReplSetName() {
    return replSetName;
  }

  public ReplEssentialOverrideModule getEssentialOverrideModule() {
    return replEssentialOverrideModule;
  }

}
