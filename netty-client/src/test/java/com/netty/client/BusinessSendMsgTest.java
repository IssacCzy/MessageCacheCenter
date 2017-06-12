package com.netty.client;

import org.junit.Test;

public class BusinessSendMsgTest {

	@Test
	public void sendBusinessSysMsg2NettyService(){
		try {
			InitDataClient client = new InitDataClient();
			String msg = "{\"uId\":\"122356\",\"type\":\"memo\",\"data\":{\"title\":\"memo最新工作进展记录\",\"content\":\"今天开始动工\",\"timeStamp\":\"1463728695871\"}}$_";
			client.sendMsg(msg);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
