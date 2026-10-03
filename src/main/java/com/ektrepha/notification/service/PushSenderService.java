package com.ektrepha.notification.service;

public interface PushSenderService {

	/**
	 * Best-effort: sends {@code title}/{@code body} to every device registered for {@code userId}.
	 * Never throws — a push failure (no devices, Firebase not configured, a stale/invalid token)
	 * is logged and otherwise swallowed, since this is always a side effect of some other action
	 * that must still succeed on its own (e.g. a nanny checking in) whether or not the push lands.
	 */
	void sendToUser(Long userId, String title, String body);

}
