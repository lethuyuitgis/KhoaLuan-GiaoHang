import { Client, type IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { tg } from './telegram';

export function createStompClient(): Client {
  return new Client({
    webSocketFactory: () => new SockJS('/ws') as any,
    connectHeaders: {
      'X-Telegram-Init-Data': tg.initData(),
    },
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    debug: () => {},
  });
}

export type LocationMessage = {
  orderId: string;
  lat: string;
  lng: string;
  accuracy: string | null;
  heading: string | null;
  recordedAt: string;
};

export type LocationCallback = (msg: LocationMessage) => void;

export function subscribeOrderLocation(
  client: Client,
  orderId: string,
  cb: LocationCallback
): () => void {
  const sub = client.subscribe(`/topic/order/${orderId}/location`, (msg: IMessage) => {
    try {
      const payload = JSON.parse(msg.body) as LocationMessage;
      cb(payload);
    } catch (e) {
      console.error('Failed to parse location message', e);
    }
  });
  return () => sub.unsubscribe();
}
