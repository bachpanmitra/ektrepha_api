package com.ektrepha.model;

/** APP_TAP = the parent app logged it (POST /me/care-manager/contacts). CM_LOG = the Care Manager logged it (POST /cm/parents/{id}/contacts). */
public enum CmContactSource {
	APP_TAP,
	CM_LOG
}
