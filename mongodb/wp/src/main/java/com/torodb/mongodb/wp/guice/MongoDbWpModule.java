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

package com.torodb.mongodb.wp.guice;

import com.google.inject.PrivateModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.torodb.core.guice.EssentialToDefaultModule;
import com.torodb.core.logging.ComponentLoggerFactory;
import com.torodb.core.logging.LoggerFactory;
import com.torodb.mongodb.commands.CommandClassifier;
import com.torodb.mongodb.core.MongoDbCoreBundle;
import com.torodb.mongodb.core.MongoDbCoreExtInt;
import com.torodb.mongodb.core.MongodMetrics;
import com.torodb.mongodb.core.MongodServer;
import com.torodb.mongodb.core.ToroErrorHandler;
import com.torodb.mongodb.wp.TorodbSafeRequestProcessor;
import com.torodb.mongowp.MongoServerConfig;
import com.torodb.mongowp.annotations.MongoWp;
import com.torodb.mongowp.bson.netty.DefaultNettyBsonLowLevelReader;
import com.torodb.mongowp.bson.netty.NettyBsonDocumentReader;
import com.torodb.mongowp.bson.netty.NettyBsonDocumentWriter;
import com.torodb.mongowp.bson.netty.NettyStringReader;
import com.torodb.mongowp.bson.netty.OffHeapNettyBsonLowLevelReader;
import com.torodb.mongowp.bson.netty.OffHeapValuesNettyBsonLowLevelReader;
import com.torodb.mongowp.bson.netty.PooledNettyStringReader;
import com.torodb.mongowp.bson.netty.pool.OnlyLikelyStringPoolPolicy;
import com.torodb.mongowp.bson.netty.pool.ShortStringPoolPolicy;
import com.torodb.mongowp.bson.netty.pool.StringPool;
import com.torodb.mongowp.bson.netty.pool.WeakMapStringPool;
import com.torodb.mongowp.commands.CommandLibrary;
import com.torodb.mongowp.commands.ErrorHandler;
import com.torodb.mongowp.server.api.RequestProcessorAdaptor;
import com.torodb.mongowp.server.callback.RequestProcessor;
import com.torodb.mongowp.server.decoder.DeleteMessageDecoder;
import com.torodb.mongowp.server.decoder.GetMoreMessageDecoder;
import com.torodb.mongowp.server.decoder.InsertMessageDecoder;
import com.torodb.mongowp.server.decoder.KillCursorsMessageDecoder;
import com.torodb.mongowp.server.decoder.MessageDecoderLocator;
import com.torodb.mongowp.server.decoder.QueryMessageDecoder;
import com.torodb.mongowp.server.decoder.UpdateMessageDecoder;
import com.torodb.mongowp.server.encoder.ReplyMessageEncoder;
import com.torodb.mongowp.server.wp.DefaultRequestIdGenerator;
import com.torodb.mongowp.server.wp.NettyMongoServer;
import com.torodb.mongowp.server.wp.ReplyMessageObjectHandler;
import com.torodb.mongowp.server.wp.RequestIdGenerator;
import com.torodb.mongowp.server.wp.RequestMessageByteHandler;
import com.torodb.mongowp.server.wp.RequestMessageObjectHandler;

import java.util.concurrent.ThreadFactory;

public class MongoDbWpModule extends PrivateModule {

  private final MongoDbCoreExtInt coreExtInt;
  private final int port;

  public MongoDbWpModule(MongoDbCoreBundle coreBundle, int port) {
    this.coreExtInt = coreBundle.getExternalInterface();
    this.port = port;
  }

  @Override
  protected void configure() {
    expose(NettyMongoServer.class);
    expose(MongoServerConfig.class);

    install(new WpToDefaultModule());

    bindCore();

    bind(NettyMongoServer.class)
        .in(Singleton.class);
    
    bind(MongoServerConfig.class)
        .toInstance((MongoServerConfig) () -> port);
    
    bind(NettyStringReader.class)
        .to(PooledNettyStringReader.class)
        .in(Singleton.class);

    configureStringPool();

    bind(RequestIdGenerator.class)
        .to(DefaultRequestIdGenerator.class);

    bind(ErrorHandler.class)
        .to(ToroErrorHandler.class)
        .in(Singleton.class);

    bind(RequestMessageByteHandler.class);

    bindMessageDecoder();

    bind(DefaultNettyBsonLowLevelReader.class)
        .in(Singleton.class);
    bind(NettyBsonDocumentReader.class)
        .in(Singleton.class);
    bind(OffHeapNettyBsonLowLevelReader.class)
        .in(Singleton.class);

    bind(OffHeapValuesNettyBsonLowLevelReader.class)
        .to(OffHeapNettyBsonLowLevelReader.class);

    bind(RequestMessageObjectHandler.class)
        .in(Singleton.class);
    bind(ReplyMessageObjectHandler.class);
    bind(TorodbSafeRequestProcessor.class)
        .in(Singleton.class);

    bind(ReplyMessageEncoder.class)
        .in(Singleton.class);
    bind(MongodMetrics.class)
        .in(Singleton.class);
    bind(NettyBsonDocumentWriter.class)
        .in(Singleton.class);

    bind(ThreadFactory.class)
        .annotatedWith(MongoWp.class)
        .to(ThreadFactory.class);
  }

  private void bindMessageDecoder() {
    bind(MessageDecoderLocator.class)
        .in(Singleton.class);
    bind(DeleteMessageDecoder.class)
        .in(Singleton.class);
    bind(GetMoreMessageDecoder.class)
        .in(Singleton.class);
    bind(InsertMessageDecoder.class)
        .in(Singleton.class);
    bind(KillCursorsMessageDecoder.class)
        .in(Singleton.class);
    bind(QueryMessageDecoder.class)
        .in(Singleton.class);
    bind(UpdateMessageDecoder.class)
        .in(Singleton.class);
  }

  private void configureStringPool() {
    bind(StringPool.class)
        //                .toInstance(new InternStringPool(
        //                        OnlyLikelyStringPoolPolicy.getInstance()
        //                                .or(new ShortStringPoolPolicy(5))
        //                ));

        //                .toInstance(new GuavaStringPool(
        //                        OnlyLikelyStringPoolPolicy.getInstance()
        //                                .or(new ShortStringPoolPolicy(5)),
        //                        CacheBuilder.newBuilder().maximumSize(100_000)
        //                ));
        .toInstance(new WeakMapStringPool(
            OnlyLikelyStringPoolPolicy.getInstance()
                .or(new ShortStringPoolPolicy(5))
        ));
  }

  @Provides
  RequestProcessor createRequestProcessorAdaptor(TorodbSafeRequestProcessor tsrp,
      ErrorHandler errorHandler) {
    return new RequestProcessorAdaptor<>(tsrp, errorHandler);
  }

  private void bindCore() {
    bind(CommandLibrary.class)
        .toInstance(coreExtInt.getCommandLibrary());
    bind(CommandClassifier.class)
        .toInstance(coreExtInt.getCommandClassifier());
    bind(MongodServer.class)
        .toInstance(coreExtInt.getMongodServer());
  }

  private static class WpToDefaultModule extends EssentialToDefaultModule {

    @Override
    protected void bindLoggerFactory() {
      bind(LoggerFactory.class)
          .toInstance(new ComponentLoggerFactory("WP"));
    }

  }

}
