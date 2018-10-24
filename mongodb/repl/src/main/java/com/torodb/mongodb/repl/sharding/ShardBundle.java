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

package com.torodb.mongodb.repl.sharding;

import com.torodb.common.util.Empty;
import com.torodb.core.bundle.AbstractBundle;

/**
 * This bundle is used to replicate from a single shard.
 *
 * Implementations can be compatible or incompatible with other concurrent shard bundles. Unless it
 * is explicitly said, it is undertand that all implementations are compatible with theirself.
 *
 * Implementations can assume there is a {@link ShardBundleConfig#getTorodBundle() torod bundle},
 * provided by the configuration.
 */
public abstract class ShardBundle extends AbstractBundle<Empty> {

  ShardBundle(ShardBundleConfig config) {
    super(config);
  }

  @Override
  public Empty getExternalInterface() {
    return Empty.getInstance();
  }


}
