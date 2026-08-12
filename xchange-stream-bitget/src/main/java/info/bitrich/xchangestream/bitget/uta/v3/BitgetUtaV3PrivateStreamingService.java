package info.bitrich.xchangestream.bitget.uta.v3;

import info.bitrich.xchangestream.bitget.BitgetStreamingAuthHelper;
import info.bitrich.xchangestream.bitget.config.Config;
import info.bitrich.xchangestream.bitget.dto.common.Operation;
import info.bitrich.xchangestream.bitget.dto.request.BitgetLoginRequest;
import info.bitrich.xchangestream.bitget.dto.request.BitgetLoginRequest.LoginPayload;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3EventNotification;
import java.io.IOException;
import java.time.Instant;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.exceptions.ExchangeException;

/**
 * Private Bitget UTA v3 transport: logs in with the classic v2 signature scheme (preimage {@code
 * epochSeconds + "GET" + "/user/verify"}, byte-identical between v2 and v3) and defers channel
 * (re)subscription until the {@code login} acknowledgement arrives.
 *
 * <p>The login acknowledgement is correlated to the connection generation it was requested under; a
 * stale {@code login} ack from an earlier connection is dropped, which prevents an old connection's
 * channels from being resubscribed on the current one.
 *
 * @since 5.1.0
 */
@Slf4j
public class BitgetUtaV3PrivateStreamingService extends BitgetUtaV3StreamingService {

  private final String apiKey;
  private final String apiSecret;
  private final String apiPassword;

  private final AtomicLong loginGeneration = new AtomicLong();

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
    connectionGeneration.incrementAndGet();
    sendLoginMessage();
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
    loginGeneration.set(getConnectionGeneration());
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
      if (!isCurrentGeneration(loginGeneration.get())) {
        log.warn(
            "Stale login acknowledgement from connection generation {} (current {}); ignoring",
            loginGeneration.get(),
            getConnectionGeneration());
        return;
      }
      if ("0".equals(notification.getCode())) {
        resubscribeChannelsAfterLogin();
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
