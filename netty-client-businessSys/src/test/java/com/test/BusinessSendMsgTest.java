package com.test;


import org.junit.Test;

import com.netty.client.InitDataClient;

public class BusinessSendMsgTest {

	@Test
	public void sendBusinessSysMsg2NettyService(){
		try {
			
			int title = 1;
			String uId = "";
			String msg = "";
			String type = "";
			
			for(int i=0;i<1000;i++){
				uId = "";
				type = "";
				
				if(i%3==1)
					type = "memo";
				else
					type = "igoal";
				
				if(i%5==1)
					uId = "1002";
				else
					uId = "1001";
					
				title += i; 
				msg = "{\"uId\":\""+uId+"\",\"type\":\""+type+"\",\"data\":{\"title\":\""+title+"\",\"content\":\"今天开始动工\",\"timeStamp\":\"1463728695871\"}}$_";
				InitDataClient client = new InitDataClient();
				client.sendMsg(msg);
				
				Thread.sleep(2000);
			}
			
			
			for(int i=0;i<10;i++){
				title += i; 
				msg = "{\"uId\":\""+uId+"\",\"type\":\""+type+"\",\"data\":{\"title\":\""+title+"\",\"content\":\"今天开始动工\",\"timeStamp\":\"1463728695871\"}}$_";
				InitDataClient client = new InitDataClient();
				client.sendMsg(msg);
			}
			
			
			/*InitDataClient client = new InitDataClient();
			msg = "{\"uId\":\"122356\",\"type\":\"memo\",\"data\":{\"title\":\"memo最新工作进展记录\",\"content\":\"今天开始动工\",\"timeStamp\":\"1463728695871\"}}$_{\"uId\":\"2134685\",\"type\":\"memo\",\"data\":{\"title\":\"memo最新工作进展记录\",\"content\":\"今天开始动工\",\"timeStamp\":\"1463728695871\"}}$_";
			client.sendMsg(msg);*/
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
