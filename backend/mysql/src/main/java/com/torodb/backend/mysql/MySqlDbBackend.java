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

package com.torodb.backend.mysql;

import com.torodb.backend.AbstractDbBackendService;
import com.torodb.backend.BackendConfig;
import com.torodb.backend.BackendConfigImpl;
import com.torodb.backend.BackendLoggerFactory;
import com.torodb.backend.TransactionIsolationLevel;
import com.torodb.backend.mysql.driver.MySqlDriverProvider;
import com.torodb.core.annotations.TorodbIdleService;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ThreadFactory;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.sql.DataSource;

/**
 * MySQL-based backend.
 */
public class MySqlDbBackend extends AbstractDbBackendService<BackendConfig> {

  private static final Logger LOGGER = BackendLoggerFactory.get(MySqlDbBackend.class);

  private final MySqlDriverProvider driverProvider;

  @Inject
  public MySqlDbBackend(@TorodbIdleService ThreadFactory threadFactory,
      BackendConfig configuration,
      MySqlDriverProvider driverProvider,
      MySqlErrorHandler errorHandler) {
    super(threadFactory, configuration, errorHandler);

    LOGGER.info("Configured MySQL backend at {}:{}", configuration.getDbHost(), configuration
        .getDbPort());

    this.driverProvider = driverProvider;
  }

  @Override
  protected DataSource getConfiguredDataSource(BackendConfig configuration, String poolName) {
    return driverProvider.getConfiguredDataSource((BackendConfigImpl) configuration, poolName);
  }

  @Override
  @Nonnull
  protected TransactionIsolationLevel getCommonTransactionIsolation() {
    return TransactionIsolationLevel.TRANSACTION_REPEATABLE_READ;
  }

  @Override
  @Nonnull
  protected TransactionIsolationLevel getSystemTransactionIsolation() {
    return TransactionIsolationLevel.TRANSACTION_REPEATABLE_READ;
  }

  @Override
  @Nonnull
  protected TransactionIsolationLevel getGlobalCursorTransactionIsolation() {
    return TransactionIsolationLevel.TRANSACTION_REPEATABLE_READ;
  }
}
