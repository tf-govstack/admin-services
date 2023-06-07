package io.mosip.hotlist.constant;

public enum NotificationTemplateCode {
	HS_UIN_BLOCK("HS_UIN_BLOCK"),
	HS_UIN_UNBLOCK("HS_UIN_UNBLOCK"),
	HS_VID_BLOCK("HS_VID_BLOCK"),
	HS_VID_UNBLOCK("HS_VID_UNBLOCK");
	
	private final String templateCode;

	NotificationTemplateCode(String templateCode) {
		this.templateCode = templateCode;
	}

	@Override
	public String toString() {
		return templateCode;
	}
	
}
