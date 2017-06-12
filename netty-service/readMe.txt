后期计划：netty service 支持一下操作：
	1、消息持久化相关操作(spring 、 mybatis 接入)；
	2、消息过期消除操作；
	3、消息状态回执：
		即 service push 消息给 client ——》
		用户查看该消息 ——》
		client 需 push 一个状态给 service ，告知 service 该消息已经被消费——》
		service 直接删除该消息  or 标记消息状态，定时删除
	注意点：a、消息排序；	b、消息过滤；


第一步：启动 netty 服务器：
	com.netty.service.WebSocketServer

第二步：
	构建 netty_client.jar
	
第三步：(business system  对应 ：netty_client_businessSys)
	business system 引用 netty_client.jar
	添加配置文件 netty_service.properties[
										netty.service.host=localhost
										netty.service.post=81
									  ]
	
第四步：(netty_clint_businessSys通过junit单元测试进行模拟消息推送)
	business system 调用 netty_client.jar api 将消息推送至 netty service

第五步：
	启动网页客户端，和 netty service 建立 socket 链接，接收 netty service 推送的消息；
	test_html/jingoal/.../...
