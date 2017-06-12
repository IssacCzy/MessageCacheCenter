package com.netty.service;

import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static io.netty.handler.codec.http.HttpHeaders.setContentLength;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.util.CharsetUtil;

/**
 * @author cuizhanyou
 * @date 2014年2月14日
 * @version 1.0
 */
public class WebSocketServerHandler extends SimpleChannelInboundHandler<Object> {
	
	private static final Logger logger = Logger.getLogger(WebSocketServerHandler.class.getName());
	private WebSocketServerHandshaker handshaker;
	
	/**
	 * 针对客户端请求类型进行消息的处理
	 */
	@Override
	public void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
		
		// 传统的HTTP接入
		//1、第一次握手请求有HTTP协议承载，所以它是一个HTTP消息，执行 handleHttpRequest方法来处理WebSocket握手请求
		if (msg instanceof FullHttpRequest) {
			logger.info(ServiceUtil.getFormateDate()+"第一次握手请求协议升级");
			handleHttpRequest(ctx, (FullHttpRequest) msg);
			logger.info(ServiceUtil.getFormateDate()+"WebSocket协议升级成功！");
		}
		
		// WebSocket接入
		//5、需要判断WebSocket请求（即WebSocketFrame类）的具体类型，对WebSocket请求消息进行处理
		else if (msg instanceof WebSocketFrame) {
			
			//判断是不是WebSocket关闭操作
			if (((WebSocketFrame) msg) instanceof CloseWebSocketFrame) {
				logger.info(ServiceUtil.getFormateDate()+"客户端发出关闭链路请求");
				//如果是关闭链路的控制消息，则关闭WebSocket连接
				handshaker.close(ctx.channel(), (CloseWebSocketFrame) ((WebSocketFrame) msg).retain());
				WebSocketServer.channelGroup.remove(ctx.channel());
				
				logger.info(ServiceUtil.getFormateDate()+"链路关闭成功！");
				return;
			}
			/*
			 * 判断是不是Ping消息，如果是，则发送一个Pong消息给客户端；
			 * WebSocket中，Ping、Pong消息用于心跳检查。客户端使用的叫做Ping，服务端对之进行的响应叫做Pong
			 */
			else if(((WebSocketFrame) msg) instanceof PingWebSocketFrame){
				logger.info(ServiceUtil.getFormateDate()+"客户端发出维持链路的心跳消息 Ping");
				//8、如果是维持链路的ping消息，则构造pong消息返回
				ctx.channel().write(new PongWebSocketFrame(((WebSocketFrame) msg).content().retain()));
				logger.info(ServiceUtil.getFormateDate()+"服务器端返回出维持链路的心跳消息 Pong");
				return ;
			}
			//判断消息的类型是否是文本消息
			else if(!(((WebSocketFrame) msg) instanceof TextWebSocketFrame)) { 
				logger.info(ServiceUtil.getFormateDate()+"仅支持文本消息，非文本消息则抛出异常");
				//9、 本例程仅支持文本消息，不支持二进制消息：非文本消息则抛出异常
				throw new UnsupportedOperationException(
							String.format("%s frame types not supported", ((WebSocketFrame) msg).getClass().getName()));
			}
			/*
			 * 如果前面三种情况[websocket关闭、pingPong心跳检查、文本类型]都不是，则frame应该是一个合法的WebSocket文本消息了
			 */
			else{
				logger.info(ServiceUtil.getFormateDate()+"************对 WebSocket请求消息进行处理!***********");
				logger.info(ServiceUtil.getFormateDate()+"Netty处理WebSocket请求：");
				//维护用户、类型type和Channel通道之间的对应关系
				logger.info(ServiceUtil.getFormateDate()+"记录当前 User 的Channel通道;");
				{
					user_Type_ChannelIds(ctx, (WebSocketFrame) msg);
				}
				
				//end：读取消息
				readMessage(ctx, (WebSocketFrame)msg);
			}
		}
	}
	
	/**
	 * 维护用户、类型type和Channel通道之间的对应关系
	 * @param ctx
	 * @param msg
	 */
	private void user_Type_ChannelIds(ChannelHandlerContext ctx, WebSocketFrame msg){
		String request_json_str = ((TextWebSocketFrame) msg).text();
		Map<String, String> request = ServiceUtil.jsonStr2Map(request_json_str);
		String uId = String.valueOf(request.get("uId"));
		
		String requestInfo = request.get("request");
		requestInfo = ServiceUtil.removeHeader_end(requestInfo);
		String[] requests = requestInfo.split("\\},\\{");
		for (int i = 0; i < requests.length; i++) {
			Map<String, String> requestInfos = ServiceUtil.jsonStr2Map(ServiceUtil.json(requests[i]));  //key:  type timestamp
			String curType = requestInfos.get("type");
			
			//1、该用户ChannelIds 是否存在
			if(WebSocketServer.channel2User.keySet().contains(uId)){
				//Map<type,List<ChannelId>
				Map<String,List<ChannelId>> type_channelIds =  WebSocketServer.channel2User.get(uId);
				if(type_channelIds.keySet().contains(curType)){
					List<ChannelId> cIds = type_channelIds.get(curType);
					if(!cIds.contains(ctx.channel().id()))
						cIds.add(ctx.channel().id());
					type_channelIds.put(curType, cIds);
					WebSocketServer.channel2User.put(uId, type_channelIds);
				}
				else{
					List<ChannelId> cIds = new ArrayList<ChannelId>();
					cIds.add(ctx.channel().id());
					type_channelIds.put(curType, cIds);
					WebSocketServer.channel2User.put(uId, type_channelIds);
				}
			}
			else{ //2、不存在直接添加到channel2User
				List<ChannelId> cIds = new ArrayList<ChannelId>();
				cIds.add(ctx.channel().id());
				Map<String,List<ChannelId>> type_channelIds = new HashMap<String, List<ChannelId>>();
				type_channelIds.put(curType, cIds);
				WebSocketServer.channel2User.put(uId, type_channelIds);
			}
		}
	}
	
	
	private void readMessage(ChannelHandlerContext ctx, WebSocketFrame frame) {
		logger.info(ServiceUtil.getFormateDate()+"根据客户端请求，筛选消息并PUSH");
		String request_json_str = ((TextWebSocketFrame)frame).text();
		Map<String,String> reqestMap = ServiceUtil.jsonStr2Map(request_json_str);
		String uId = reqestMap.get("uId");
		
		String requestInfo = reqestMap.get("request");
		requestInfo = ServiceUtil.removeHeader_end(requestInfo);
		String[] requests = requestInfo.split("\\},\\{");
		
		if(requests.length>0){
			//得到消息队列号
			int MQCode = ServiceUtil.getMQCode(Integer.valueOf(uId));
			StringBuffer response = new StringBuffer();
			
			for (int i = 0; i < requests.length; i++) {
				Map<String, String> request = ServiceUtil.jsonStr2Map(ServiceUtil.json(requests[i]));  //key:  type timestamp
				String curType = request.get("type");
				long timeStamp = Long.valueOf(request.get("timeStamp"));
				int status = Integer.valueOf(request.get("status"));	//全量 or 增量
				
				if(curType.equals("all")){
					Set<String> allTypes = ServiceUtil.getMQ(MQCode).keySet();
					
					for (String t : allTypes) {
						//Map<uId,List<json>>
						Map<String,List<String>> user_date = ServiceUtil.getMQ(MQCode).get(t);
						Set<String> uIds = user_date.keySet();
						if(uIds.contains(uId)){
							for(String id :uIds){
								if(id.equals(uId)){
									List<String> jsonInfos = user_date.get(id);
									
									//时间和最小的一条比较，如果
									long minTime = ServiceUtil.getTimeStampByInfo(jsonInfos.get(0));
									long maxTime = ServiceUtil.getTimeStampByInfo(jsonInfos.get(jsonInfos.size()-1));
									
//									//落点为 a，去业务系统取全量消息
//									if(Long.valueOf(timeStamp)<minTime){
//										Util.pushMessage(uId,"All",curType);
//										return;
//									}
//									
//									//落点为 c，直接返回空
//									if(Long.valueOf(timeStamp)>maxTime){
//										Util.pushMessage(uId,"Nothing",curType);
//										return;
//									}
									
									for (String info : jsonInfos) {
										Map<String,String> infoMap = ServiceUtil.jsonStr2Map(info);
										
										//如果当前消息时间大于请求传递的时间，则将消息返回
										if(Long.valueOf(timeStamp)<Long.valueOf(infoMap.get("timeStamp"))){
											response.append(",");
											response.append(info);
										}
										//如果当前时间小于请求传递的时间，但被标为特殊数据，则同样将消息返回
										if(Long.valueOf(timeStamp)>Long.valueOf(infoMap.get("timeStamp")) && Integer.valueOf(infoMap.get("mark"))==ServiceUtil.MESSAGE_MARK_SPE){
											response.append(",");
											response.append(info);
										}
									}
									
								}
							}
						}
					}
					
				}
				else{
					Set<String> allTypes = ServiceUtil.getMQ(MQCode).keySet();
					
					if (allTypes.contains(curType)){
						//Map<uId,List<json>>
						Map<String,List<String>> user_date = ServiceUtil.getMQ(MQCode).get(curType);
						Set<String> uIds = user_date.keySet();
						if(uIds.contains(uId)){
							for(String id :uIds){
								if(id.equals(uId)){
									List<String> jsonInfos= user_date.get(id);
									
									//时间和最小的一条比较，如果
									long minTime = ServiceUtil.getTimeStampByInfo(jsonInfos.get(0));
									long maxTime = ServiceUtil.getTimeStampByInfo(jsonInfos.get(jsonInfos.size()-1));
									
//									//落点为 a，去业务系统取全量消息
//									if(Long.valueOf(timeStamp)<minTime){
//										Util.pushMessage(uId,"All",curType);
//										return;
//									}
//									
//									//落点为 c，直接返回空
//									if(Long.valueOf(timeStamp)>maxTime){
//										Util.pushMessage(uId,"Nothing",curType);
//										return;
//									}
									
									for (String info : jsonInfos) {
										Map<String,String> infoMap = ServiceUtil.jsonStr2Map(info);
										
										if(Long.valueOf(timeStamp)<Long.valueOf(infoMap.get("timeStamp"))){
											response.append(",");
											response.append(info);
										}
									}
								}
							}
						}
					}
					
				}
			}
			
			if(null!=response && !"".equals(response) && response.length()>0){
				pushMessage(ctx.channel(),response.substring(1).toString());
			}
			else{
				pushMessage(ctx.channel(),"Nothing");
				logger.info(ServiceUtil.getFormateDate()+"如果没有新消息，则告知客户端 Nothing;");
			}
		}
		
	}
	
	
	private void pushMessage(Channel channel,String message){
		if(null!=channel && channel.isActive()){
			channel.write(new TextWebSocketFrame(message));
			channel.flush();
		}
	}
	
	@Override
	public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
		logger.info(ServiceUtil.getFormateDate()+"将Channel通道注册到ChannelGroup来管理");
		WebSocketServer.channelGroup.add(ctx.channel());
	}
	
	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
		logger.info(ServiceUtil.getFormateDate()+"将相应缓冲区中的消息PUSH给客户端");
		ctx.flush();	//flush 负责将发送环形数组中缓存的消息写入到SocketChannel中，发送给对方
	}

	/**
	 * 处理HTTP请求：处理握手请求，升级协议
	 * 一个WebSocket会话的开始其实是由一个HTTP请求开始的
	 * 
	 * @param ctx
	 * @param req
	 * @throws Exception
	 */
	private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {

		/*
		 * 2、首先对握手请求消息进行判断，如果消息头中没有包含Upgrade字段，或者它的值不是websocket，则返回400响应
		 * 根据HTTP1.1的定义，这个HTTP请求的头信息中必须包含一个Upgrade:websocket的key-value。
		 * 如果不包含则表明这不是一个标准的WebSocket会话的开始，我们可以调用sendHttpResponse输出一个Bad Request错误
		 */
		if (!req.getDecoderResult().isSuccess() || (!"websocket".equals(req.headers().get("Upgrade")))) {
			sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
			return;
		}

		/*
		 * 3、握手请求简单验证通过后，则构造握手工厂
		 * 接下来开始进行WebSocket连接，这是通过创建一个WebSocket的handshaker对象来完成的
		 */
		WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory("ws://localhost:8080/websocket", null, false);
		
		/*
		 * 4、创建WebSocketServerHandshaker实例，通过它构造握手相应消息返回给客户端
		 * 如果handshaker创建失败，发送错误消息，否则开始进行握手动作
		 */
		handshaker = wsFactory.newHandshaker(req);
		if (handshaker == null) {
			WebSocketServerHandshakerFactory.sendUnsupportedWebSocketVersionResponse(ctx.channel());
		} else {
			//通过它构造握手响应消息给客户端，同时将WebSocket相关的编码和解码类动态添加到ChannelPipeline中，用于WebSocket消息的编解码
			//后面的业务Handler就可以直接对WebSocket对象进行操作
			handshaker.handshake(ctx.channel(), req);
		}
		
	}

	/**
	 * 用于向客户端输出一些HTTP消息
	 * @param ctx
	 * @param req
	 * @param res
	 */
	private static void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, FullHttpResponse res) {
		
		/*
		 *  返回应答给客户端
		 *  首先它判断服务器要输出的是不是HTTP200状态（准备就绪），如果不是，表明出错了，将HTTP状态码输出给客户端
		 */
		if (res.getStatus().code() != 200) {
			ByteBuf buf = Unpooled.copiedBuffer(res.getStatus().toString(), CharsetUtil.UTF_8);
			res.content().writeBytes(buf);
			buf.release();
			setContentLength(res, res.content().readableBytes());
		}

		/*
		 *  如果是非Keep-Alive，关闭连接
		 *  当然要判断一下keep alive 和 HTTP 200标志
		 */
		ChannelFuture f = ctx.channel().writeAndFlush(res);
		if (!isKeepAlive(req) || res.getStatus().code() != 200) {
			f.addListener(ChannelFutureListener.CLOSE);
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		cause.printStackTrace();
		ctx.close();
		logger.info(ServiceUtil.getFormateDate()+"发现异常，关闭链路！");
	}
}
