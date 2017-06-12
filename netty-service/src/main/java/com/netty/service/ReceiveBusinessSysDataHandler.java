package com.netty.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import com.netty.ServiceUtil;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

/**
 * 接收业务系统push过来的消息并处理
 * 		1、如果channel通道正常，则直接将消息push客户端浏览器；
 * 		2、如果channel异常/关闭，则将消息缓存到消息队列中；
 * 
 * @author cuizhanyou
 * @date 2014年2月14日
 * @version 1.0
 */
public class ReceiveBusinessSysDataHandler extends ChannelHandlerAdapter {

	private static final Logger logger = Logger.getLogger(ReceiveBusinessSysDataHandler.class.getName());
	private int counter;

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		logger.info(ServiceUtil.getFormateDate()+"第"+(++counter)+"次  Netty服务器接收到业务系统发来的消息");
		
		/*
		 * TimeServer中添加了LineBasedFrameDecoder StringDecoder 两个解码器后，
		 * 返回的msg直接就是删除回车换行符后的消息，不需要考虑处理半包及编码问题；
		 */
		String request = (String) msg;
		
		System.out.println("*******************");
		System.out.println("msg from client:"+request);
		System.out.println("*******************");
		
		Map<String, String> infoMap = ServiceUtil.jsonStr2Map(request);
		String uId = String.valueOf(infoMap.get("uId"));
		String type = String.valueOf(infoMap.get("type"));
		String jsonStr = String.valueOf(infoMap.get("data"));
		
		//将消息保存或push
		pushOrSaveInfo(uId, type, jsonStr);
		
		String response = "Netty服务器已处理了您的消息";
		response += "$_";
		ByteBuf resp = Unpooled.copiedBuffer(response.getBytes());
		ctx.writeAndFlush(resp);
		logger.info(ServiceUtil.getFormateDate()+"Netty处理好业务系统消息后通知业务系统：已处理完毕（或已Push给目标对象，或已经存储）");
	}
	
	private void pushOrSaveInfo(String uId,String type,String jsonStr){
		
		jsonStr = ServiceUtil.addType(jsonStr, type);
		
		//首先根据Uid获取对应的channel ，判断通道是否激活，如果是活跃的，则直接push给客户端，如果已断裂则存储到Map中
		if(WebSocketServer.channel2User.keySet().contains(uId)){
			Map<String,List<ChannelId>> type_channelIds = WebSocketServer.channel2User.get(uId);
			if(type_channelIds.keySet().contains(type)){
				List<ChannelId> channelIds = type_channelIds.get(type);
				int failCount = 0;
				for (ChannelId channelId : channelIds) {
					if(channelId!=null){	//如果通道存在，并且是激活的状态，则直接push给客户端
						Channel channel = WebSocketServer.channelGroup.find(channelId);
						if(null!=channel && channel.isActive()){
							channel.write(new TextWebSocketFrame(jsonStr));
							/*
							 * flush 负责将发送环形数组中缓存的消息写入到SocketChannel中，发送给对方
							 */
							channel.flush();	
							logger.info(ServiceUtil.getFormateDate()+"当前消息的接收对象的Channel如果是 Keep-Alive，则直接PUSH给客户端，不存储");
						}
						else
							failCount ++;
					}
				}
				if(failCount == channelIds.size())
					insertMQ(uId, type, jsonStr);
			}
			else
				insertMQ(uId, type, jsonStr);
			
			if(type_channelIds.keySet().contains("all")){
				List<ChannelId> channelIds = type_channelIds.get("all");
				for (ChannelId channelId : channelIds) {
					if(channelId!=null){	//如果通道存在，并且是激活的状态，则直接push给客户端
						Channel channel = WebSocketServer.channelGroup.find(channelId);
						if(null!=channel && channel.isActive()){
							channel.write(new TextWebSocketFrame(jsonStr));
							channel.flush();
							logger.info(ServiceUtil.getFormateDate()+"当前消息的接收对象的Channel如果是 Keep-Alive，则直接PUSH给客户端，不存储");
						}
					}
				}
			}
			
		}
		else
			insertMQ(uId, type, jsonStr);
					
	}
	
	/**
	 * 消息保存到消息队列中
	 * @param uId
	 * @param type
	 * @param jsonStr
	 */
	private void insertMQ(String uId, String type, String jsonStr){
		logger.info(ServiceUtil.getFormateDate()+"当前消息的接收对象的Channel链路已经断裂，则将消息根据算法存储到相应队列里面");
		
		List<String> json_datas = null;
		Map<String,List<String>> userData = null;
		
		//得到消息队列号
		int MQCode = ServiceUtil.getMQCode(Integer.valueOf(uId));
		Set<String> key_types = ServiceUtil.getMQKeys(MQCode);
		
		if(null!=key_types && key_types.contains(type)){
			
			logger.info(ServiceUtil.getFormateDate()+"判断队列中该用户该类型的消息条数");
			ServiceUtil.checkMQ(type,uId,MQCode);
			
			userData = ServiceUtil.getMQ(MQCode).get(type);
			if(userData.keySet().contains(uId))
				json_datas = userData.get(uId);
			else
				json_datas = new ArrayList<String>();
			
			//添加时考虑当前消息和队列中最大时间的消息比较，如果大于Max,则添加特殊标记1，else 0
//			String last = json_datas.get(json_datas.size()-1);
			String last = "";
			if(json_datas.size()>0)
				last = json_datas.get(json_datas.size()-1);
			
			Map<String,String> lastMap = ServiceUtil.jsonStr2Map(last);
			Map<String,String> newMap = ServiceUtil.jsonStr2Map(jsonStr);
			
//			if(Long.valueOf(lastMap.get("timeStamp"))>Long.valueOf(newMap.get("timeStamp"))){
//				//添加特殊标识
//				jsonStr = ServiceUtil.addMark(jsonStr,1);
//			}
//			else
				jsonStr = ServiceUtil.addMark(jsonStr,0);
			
			json_datas.add(jsonStr);
			userData.put(uId, ServiceUtil.orderList(json_datas));
			ServiceUtil.getMQ(MQCode).put(type, userData);
		}
		else{
			userData = new HashMap<String,List<String>>();
			
			if(userData.keySet().contains(uId))
				json_datas = userData.get(uId);
			else
				json_datas = new ArrayList<String>();
			
			jsonStr = ServiceUtil.addMark(jsonStr,0);
			json_datas.add(jsonStr);
			userData.put(uId, ServiceUtil.orderList(json_datas));
			
			ServiceUtil.getMQ(MQCode).put(type, userData);
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		cause.printStackTrace();
		ctx.close();
		logger.info(ServiceUtil.getFormateDate()+"Netty处理业务系统消息时发现异常，关闭链路！");
	}
}
