package info.bitrich.xchangestream.bitget.uta.v3;

import info.bitrich.xchangestream.bitget.BitgetStreamingAuthHelper;
import info.bitrich.xchangestream.bitget.config.Config;
import info.bitrich.xchangestream.bitget.dto.common.Operation;
import info.bitrich.xchangestream.bitget.dto.request.BitgetLoginRequest;
import info.bitrich.xchangestream.bitget.dto.request.BitgetLoginRequest.LoginPayload;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3EventNotification;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3WsNotification;
import info.bitrich.xchangestream.service.exception.NotConnectedException;
import io.reactivex.rxjava3.core.Observable;
import java.io.IOException;
import java.time.Instant;
import java.util.Map.Entry;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.exceptions.ExchangeException;

/**
 * Private Bitget UTA v3 transport: logs in with the classic v2 signature scheme (preimage {@code
 * epochSeconds + "GET" + "/user/verify"}, byte-identical between v2 and v3) and defers channel
 * (re)subscription until the {@code login} acknowledgement arrives.
 *
 * <p>The per-connection message gate (see {@link
 * BitgetUtaV3StreamingService#gateByConnectionGeneration}) stamps each connection with the
 * generation it was resubscribed under; a stale {@code login} ack delivered late by a previous
 * connection's callback is dropped before it can reach this handler, so an old connection's
 * channels can never be resubscribed on the current one.
 *
 * @since 5.1.0
 */
@Slf4j
public class BitgetUtaV3PrivateStreamingService extends BitgetUtaV3StreamingService {

  private final String apiKey;
  private final String apiSecret;
  private final String apiPassword;

  /** Serializes login-ack processing against concurrent subscription registration. */
  private final Object loginLock = new Object();

  /** Whether the current connection's {@code login} acknowledgement has been received. */
  private volatile boolean authenticated;

  public BitgetUtaV3PrivateStreamingService(
      String apiUri, String apiKey, String apiSecret, String apiPassword) {
    super(apiUri);
    this.apiKey = apiKey;
    this.apiSecret = apiSecret;
    this.apiPassword = apiPassword;
  }

  /** Sends the login message right after connecting; channel resubscription waits for the ack. */
  @Override
  public void resubscribeChannels() {
    synchronized (loginLock) {
      authenticated = false;
      connectionGeneration.incrementAndGet();
      stampCurrentConnection();
      sendLoginMessage();
    }
  }

  /**
   * Registers a channel subscription but defers its subscribe frame until the {@code login}
   * acknowledgement for the current connection has been received.
   *
   * <p>The server rejects any channel frame sent before login completes, so a caller subscribing
   * right after {@code connect()} (which completes on the socket handshake, before the login ack)
   * would otherwise get an error acknowledgement that kills the stream before login even lands.
   * Registration happens unconditionally under {@link #loginLock} and the frame is sent either
   * immediately (login already acked, checked under the same lock) or by {@link
   * #resubscribeChannelsAfterLogin()} when the ack flushes the registered channels; the lock
   * serializes the two so a registration can never fall through both paths.
   */
  @Override
  public Observable<BitgetUtaV3WsNotification> subscribeChannel(
      String channelName, Object... args) {
    final String subscriptionUniqueId = getSubscriptionUniqueId(channelName, args);
    log.info("Subscribing to subscriptionUniqueId={}, args={}", subscriptionUniqueId, args);

    return Observable.<BitgetUtaV3WsNotification>create(
            e -> {
              synchronized (loginLock) {
                if (!isSocketOpen()) {
                  e.onError(new NotConnectedException());
                  return;
                }
                boolean[] newlyCreated = {false};
                channels.computeIfAbsent(
                    subscriptionUniqueId,
                    cid -> {
                      newlyCreated[0] = true;
                      return new Subscription(e, channelName, args);
                    });
                if (newlyCreated[0] && authenticated) {
                  try {
                    sendMessage(getSubscribeMessage(channelName, args));
                  } catch (IOException throwable) {
                    e.onError(throwable);
                  }
                }
              }
            })
        .doOnDispose(
            () -> {
              if (channels.remove(subscriptionUniqueId) != null) {
                try {
                  sendMessage(getUnsubscribeMessage(subscriptionUniqueId, args));
                } catch (IOException e) {
                  log.debug(
                      "Failed to unsubscribe channel: {} {}", subscriptionUniqueId, e.toString());
                }
              }
            })
        .share();
  }

  /** Re-sends every subscribed channel after a successful login. */
  public void resubscribeChannelsAfterLogin() {
    for (Entry<String, Subscription> entry : channels.entrySet()) {
      try {
        Subscription subscription = entry.getValue();
        sendMessage(getSubscribeMessage(subscription.getChannelName(), subscription.getArgs()));
      } catch (IOException e) {
        log.error("Failed to reconnect channel: {}", entry.getKey());
      }
    }
  }

  @SneakyThrows
  private void sendLoginMessage() {
    Instant timestamp = Instant.now(Config.getInstance().getClock());
    BitgetLoginRequest bitgetLoginRequest =
        BitgetLoginRequest.builder()
            .operation(Operation.LOGIN)
            .payload(
                LoginPayload.builder()
                    .apiKey(apiKey)
                    .passphrase(apiPassword)
                    .timestamp(timestamp)
                    .signature(BitgetStreamingAuthHelper.sign(timestamp, apiSecret))
                    .build())
            .build();
    sendMessage(objectMapper.writeValueAsString(bitgetLoginRequest));
  }

  @Override
  protected void handleEventNotification(BitgetUtaV3EventNotification notification) {
    if (notification.getEvent() == BitgetUtaV3EventNotification.Event.LOGIN) {
      if ("0".equals(notification.getCode())) {
        synchronized (loginLock) {
          authenticated = true;
          resubscribeChannelsAfterLogin();
        }
      } else {
        String failure =
            String.format(
                "Bitget UTA v3 private WebSocket login rejected: code=%s, msg=%s",
                notification.getCode(), notification.getMessage());
        log.error(failure);
        // The socket handshake already completed, so connect() reported success; surface the
        // rejection on every private channel stream and drop the unauthenticated connection so
        // isSocketOpen() and reconnect logic observe reality instead of leaving subscribers to
        // hang on acknowledgements that will never arrive.
        failAllChannels(new ExchangeException(failure));
        disconnect().subscribe();
      }
      return;
    }
    super.handleEventNotification(notification);
  }
}
