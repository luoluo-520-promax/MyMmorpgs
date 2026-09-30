package cn.itcast.demo.mymmorpg.net;

import cn.itcast.demo.mymmorpg.handler.MessageIoDispatcher;
import io.jpower.kcp.netty.ChannelOptionHelper;
import io.jpower.kcp.netty.UkcpChannel;
import io.jpower.kcp.netty.UkcpChannelOption;
import io.jpower.kcp.netty.UkcpServerChannel;
import io.netty.bootstrap.UkcpServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * UDP/KCP 游戏端口：与 {@link BaseServer} 共用帧编解码与 MessageIoDispatcher（默认同端口双协议）。
 */
@Component
@ConditionalOnProperty(name = "game.kcp.enabled", havingValue = "true", matchIfMissing = true)
@DependsOn({"serverLayer", "gameContext"})
public class GameKcpServer {

    private static final Logger log = LoggerFactory.getLogger(GameKcpServer.class);

    @Value("${game.netty.host:0.0.0.0}")
    private String fallbackHost;
    @Value("${game.netty.port:8089}")
    private int fallbackPort;
    @Value("${game.kcp.port:0}")
    private int kcpPortOverride;
    @Value("${game.kcp.interval:20}")
    private int intervalMs;
    @Value("${game.kcp.mtu:1400}")
    private int mtu;
    @Value("${game.kcp.write-buffer-low-water-mark:32768}")
    private int writeBufferLowWaterMark;
    @Value("${game.kcp.write-buffer-high-water-mark:65536}")
    private int writeBufferHighWaterMark;
    @Value("${game.netty.read-idle-seconds:60}")
    private int readIdleSeconds;

    private final ServerConfig serverConfig;
    private final MessageIoDispatcher messageIoDispatcher;

    private EventLoopGroup group;
    private ChannelFuture bindFuture;

    public GameKcpServer(ServerConfig serverConfig, MessageIoDispatcher messageIoDispatcher) {
        this.serverConfig = serverConfig;
        this.messageIoDispatcher = messageIoDispatcher;
    }

    @PostConstruct
    public void start() {
        int port = kcpPortOverride > 0
                ? kcpPortOverride
                : (serverConfig.getServerPort() > 0 ? serverConfig.getServerPort() : fallbackPort);
        String host = StringUtils.hasText(serverConfig.getServerIp()) ? serverConfig.getServerIp() : fallbackHost;
        group = new NioEventLoopGroup();
        try {
            UkcpServerBootstrap b = new UkcpServerBootstrap();
            b.group(group)
                    .channel(UkcpServerChannel.class)
                    .childHandler(new ChannelInitializer<UkcpChannel>() {
                        @Override
                        protected void initChannel(UkcpChannel ch) {
                            int low = Math.max(1024, writeBufferLowWaterMark);
                            int high = Math.max(low + 1024, writeBufferHighWaterMark);
                            ch.config().setWriteBufferWaterMark(new WriteBufferWaterMark(low, high));
                            ch.pipeline().addLast(new IdleStateHandler(readIdleSeconds, 0, 0));
                            ch.pipeline().addLast(new NettyIdleCloseHandler());
                            ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(1 << 20, 0, 4, 0, 4));
                            ch.pipeline().addLast(new GameMessageDecoder());
                            ch.pipeline().addLast(messageIoDispatcher);
                            ch.pipeline().addLast(new GameMessageEncoder());
                        }
                    });
            ChannelOptionHelper.nodelay(b, true, intervalMs, 2, true)
                    .childOption(UkcpChannelOption.UKCP_MTU, mtu)
                    .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK,
                            new WriteBufferWaterMark(
                                    Math.max(1024, writeBufferLowWaterMark),
                                    Math.max(writeBufferLowWaterMark + 1024, writeBufferHighWaterMark)));
            bindFuture = b.bind(host, port).sync();
            log.info("GameKcpServer (UDP/KCP) listening {}:{} interval={} mtu={} writeMark={}/{}",
                    host, port, intervalMs, mtu, writeBufferLowWaterMark, writeBufferHighWaterMark);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            shutdownQuietly();
            throw new IllegalStateException("GameKcpServer start interrupted", e);
        } catch (Exception e) {
            shutdownQuietly();
            throw e;
        }
    }

    @PreDestroy
    public void stop() {
        try {
            if (bindFuture != null && bindFuture.channel() != null) {
                bindFuture.channel().close().syncUninterruptibly();
            }
        } finally {
            shutdownQuietly();
        }
        log.info("GameKcpServer stopped");
    }

    private void shutdownQuietly() {
        if (group != null) {
            group.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        }
    }
}
