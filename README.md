# APNs Client 관리 방식 고민

## 배경
APNs(Apple Push Notification Service)를 이용하여 푸시 알림을 전송하는 기능을 구현하면서, 서로 다른 인증서를 사용하는 ApnsClient 객체를 두 개 생성해야 하는 요구사항이 발생했습니다.
처음에는 `HashMap`을 사용하여 `ApnsClient`객체를 관리하는 방법을 고려했으나, 이 방식이 적절한지 확신이 서지 않았습니다. 
이에 따라 `ApnsClient`의 공식 문서를 살펴보며 올바른 설계 방안을 고민하게 되었습니다.

APNs(Apple Push Notification Service)는 내부적으로 `Connection Pool`을 관리하여 자동으로 연결을 설정하고 HTTP/2 프로토콜을 사용해 지속적인 연결을 유지합니다. 따라서 여러 개의 `ApnsClient` 인스턴스를 생성하는 것이 과연 효율적인지 검토할 필요가 있었습니다.

이에 대한 답을 찾기 위해 APNsClient의 공식 문서를 확인하였고, 이를 바탕으로 보다 적절한 관리 방안을 도출했습니다.


## APNs(Apple Push Notification Service)란?
APNs는 Apple의 푸시 알림 서비스로, 클라이언트는 APNs 게이트웨이에 푸시 알림을 전송하는 역할을 합니다. HTTP Client와 유사하게 서버와 HTTP 통신을 위한 라이브러리 또는 도구입니다.

## 인증 방식
APNs 서버와의 인증 방식에는 두 가지가 있습니다.

### 1. TLS 기반 인증
- 공급자 인증서를 사용하여 APNs와의 보안 연결을 설정합니다.
- 서버 수준에서 신뢰를 구축하므로 notification에는 `payload`와 `device token`만 포함하면 됩니다.
- 인증 토큰이 필요 없으므로 notification 크기가 작아지는 장점이 있습니다.
- 단점으로는 인증서가 하나의 앱에만 적용되므로 여러 앱에 대해 알림을 보내려면 각각의 인증서를 만들어야 합니다.

### 2. 토큰 기반 인증
- APNs와 stateless한 통신을 제공합니다.
- 서버에서 인증서를 관리할 필요가 없어 TLS 기반보다 빠른 응답 속도를 가집니다.
- 하나의 토큰으로 여러 공급자 서버에서 사용할 수 있으며, 회사의 모든 앱에 대한 notification을 처리할 수 있습니다.
- 단점으로는 각 요청마다 토큰을 포함해야 하므로 notification 크기가 상대적으로 커질 수 있습니다.

## ApnsClient 생성
`ApnsClient`는 `ApnsClientBuilder`를 사용하여 생성합니다.

```java
new ApnsClientBuilder()
      .setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST)
      .setSigningKey(signingKey)
      .setEventLoopGroup(new NioEventLoopGroup())
      .build();
```

- `EventLoopGroup`을 지정하면 여러 클라이언트가 동시에 실행될 때 스레드 개수를 제어할 수 있습니다.
- 지정하지 않으면 `ApnsClient`가 자체적으로 단일 스레드 이벤트 루프 그룹을 생성하여 관리합니다.

## 클라이언트 연결 관리
- 내부적으로 `Connection Pool`을 관리하며, 필요할 때 자동으로 APNs 서버와 연결을 설정합니다.
- 클라이언트를 별도로 시작할 필요 없이 생성 즉시 사용 가능합니다.
- `ApnsClient`는 `ApnsChannelPool`을 사용해 Connection Pool을 생성하여 관리합니다.
- HTTP/2 프로토콜 기반으로 동작하여 `Keep-Alive`를 기본적으로 지원하며, 지속적인 연결을 유지한 채 여러 개의 notification을 동시에 전송할 수 있습니다.

## 알림 전송
클라이언트는 APNs 서버로 보낸 알림을 비동기적으로 처리합니다.
`sendNotification` 메서드는 `CompletableFuture`를 기반으로 한 `PushNotificationFuture` 객체를 반환하여 비동기적으로 푸시 알림을 전송합니다.

```java
public <T extends ApnsPushNotification> PushNotificationFuture<T, PushNotificationResponse<T>> sendNotification(T notification) {
    PushNotificationFuture<T, PushNotificationResponse<T>> responseFuture = new PushNotificationFuture<>(notification);
    
    if (!this.isClosed.get()) {
        long notificationId = this.nextNotificationId.getAndIncrement();
        this.channelPool.acquire().addListener(acquireFuture -> {
            if (acquireFuture.isSuccess()) {
                Channel channel = acquireFuture.getNow();
                channel.writeAndFlush(responseFuture).addListener(future -> {
                    if (future.isSuccess()) {
                        this.metricsListener.handleNotificationSent(this, notificationId);
                    }
                });
                this.channelPool.release(channel);
            } else {
                responseFuture.completeExceptionally(acquireFuture.cause());
            }
        });
    } else {
        responseFuture.completeExceptionally(CLIENT_CLOSED_EXCEPTION);
    }
    
    return responseFuture;
}
```

### 메서드 흐름
1. `sendNotification()`이 호출되면 `PushNotificationFuture` 객체를 생성합니다.
2. `channelPool.acquire()`를 호출하여 비동기적으로 APNs 서버와의 연결을 위한 채널을 확보합니다.
3. 채널 확보가 완료되면 `channel.writeAndFlush(responseFuture)`를 통해 푸시 알림을 비동기적으로 전송합니다.
4. `writeAndFlush()` 이후 `addListener()`를 사용하여 전송 성공 여부를 확인하고, 실패 시 예외를 처리합니다.
5. `channelPool.release(channel)`을 호출하여 사용한 채널을 풀에 반환합니다.
6. `responseFuture.whenComplete()`를 활용하여 APNs 서버의 응답을 비동기적으로 처리합니다.

## 결론
- `ApnsClient`는 내부적으로 채널 풀(`ApnsChannelPool`)을 관리하므로, 여러 개의 `ApnsClient` 인스턴스를 생성하지 않고 하나의 인스턴스를 공유하는 것이 성능적으로 더 효율적입니다.
- 여러 개의 `ApnsClient` 인스턴스를 생성하면 각 클라이언트가 별도의 연결 풀을 유지해야 하므로 오히려 비효율적입니다.

따라서 다음과 같이 빈으로 등록하여 사용했습니다.

```java
@Bean(name = "apnsClient1")
public ApnsClient apnsClient1() {
    try {
        File p8 = new File(homepath + p8Path1);
        ApnsSigningKey signingKey = ApnsSigningKey.loadFromPkcs8File(p8, teamId, keyId);
        log.info("Apns client1 initialized");
        return new ApnsClientBuilder()
                .setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST)
                .setEventLoopGroup(new NioEventLoopGroup())
                .setSigningKey(signingKey)
                .build();
    } catch (IOException | NoSuchAlgorithmException | InvalidKeyException e) {
        log.error("Apns client1 initialization failed");
        throw new RuntimeException("Failed to initialize ApnsClient1", e);
    }
}

@Bean(name = "apnsClient2")
public ApnsClient apnsClient2() {
    try {
        File p8 = new File(homepath + p8Path2);
        ApnsSigningKey signingKey = ApnsSigningKey.loadFromPkcs8File(p8, teamId, keyId);
        log.info("Apns client2 initialized");
        return new ApnsClientBuilder()
                .setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST)
                .setSigningKey(signingKey)
                .build();
    } catch (IOException | NoSuchAlgorithmException | InvalidKeyException e) {
        log.error("Apns client2 initialization failed");
        throw new RuntimeException("Failed to initialize ApnsClient2", e);
    }
}
```

이렇게 함으로써, 각 인증서에 대한 별도의 `ApnsClient` 객체를 빈으로 등록하여 효율적으로 관리할 수 있도록 구성했습니다.

