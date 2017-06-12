package com.netty;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import com.netty.service.WebSocketServer;

import net.sf.json.JSONObject;

public class ServiceUtil {

	private static final Logger logger = Logger.getLogger(ServiceUtil.class.getName());
	public static final int MESSAGE_MARK_SPE = 1;
	public static final int MESSAGE_MARK_NOSPE = 0;
	
	public static final int ISFULL = 1;  //全量
	
	private static final int MESSAGE_UPLINE = 50;
	
	public static String getFormateDate(){
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		return sdf.format(new Date())+"-----";
	}
	
	@SuppressWarnings("rawtypes")
	public static Map<String,String> jsonStr2Map(String jsonString) {

		logger.info(getFormateDate()+"json 格式字符串转换为 Map");
		
		JSONObject jsonObject = JSONObject.fromObject(jsonString);
        Map<String,String> result = new HashMap<String,String>();
        Iterator iterator = jsonObject.keys();
        String key = null;
        String value = null;
        
        while (iterator.hasNext()) {
            key = (String) iterator.next();
            value = jsonObject.getString(key);
            result.put(key, value);
        }
        return result;
    }
	
	public static String getInfo(int i){
		logger.info(getFormateDate()+"业务系统模拟器动态获取消息实例");
		
		if(i%6==0 || i%6==3)
			return "memo_memo最新工作进展记录"+(i+1)+"_今天开始动工";
		else if(i%6 ==4)
			return "plan_plan2015年第一季度工作计划"+(i+1)+"_本季度的主要工作是整个协同办公平台的重构";
		else
			return "igoal_module4igoal5.2动态"+(i+1)+"_2015年主线进度";
			
		
//		return "igoal_module4igoal5.2动态"+(i+1)+"_2015年主线进度";
	}
	
	public static String getTimeStamp(){
		logger.info(getFormateDate()+"业务系统模拟器获取时间戳——杜撰消息时使用");
		long curTimeStamp = System.currentTimeMillis();
		return String.valueOf(curTimeStamp);
	}
	
	public static int getMQCode(int uId){
		logger.info(getFormateDate()+"Netty服务器计算业务消息所属消息队列");
		
		int code = uId%1000;
		if(0<= code && code<=199)
			return 1;
		if(200<= code && code<=399)
			return 2;
		if(400<= code && code<=599)
			return 3;
		if(600<= code && code<=799)
			return 4;
		if(800<= code && code<=999)
			return 5;
		return 1;
	}
	
	public static Set<String> getMQKeys(int code){
		
		if(code==1)
			return WebSocketServer.info_queue_1.keySet();
		if(code==2)
			return WebSocketServer.info_queue_2.keySet();
		if(code==3)
			return WebSocketServer.info_queue_3.keySet();
		if(code==4)
			return WebSocketServer.info_queue_4.keySet();
		if(code==5)
			return WebSocketServer.info_queue_5.keySet();
		
		return null;
	}
	
	public static Map<String,Map<String,List<String>>> getMQ(int code){
		
		if(code==1)
			return WebSocketServer.info_queue_1;
		if(code==2)
			return WebSocketServer.info_queue_2;
		if(code==3)
			return WebSocketServer.info_queue_3;
		if(code==4)
			return WebSocketServer.info_queue_4;
		if(code==5)
			return WebSocketServer.info_queue_5;
		
		return null;
	}
	
	public static void checkMQ(String curType, String uId, int code){
		
		logger.info(ServiceUtil.getFormateDate()+"首先判断队列中该用户该类型的消息条数");
		logger.info(ServiceUtil.getFormateDate()+"-----如果已经到达上限50条，则判断第一条是否被特殊标记");
		logger.info(ServiceUtil.getFormateDate()+"-----未被特殊标记则直接删除");
		logger.info(ServiceUtil.getFormateDate()+"-----已被特殊标记则清空消息队列，告知客户端到业务系统去取数据，然后断开Channel通道");
		
		Map<String,List<String>> userData = null;
		List<String> list_datas = null;
		
		Set<String> key_types = getMQ(code).keySet();
		if(null!=key_types && key_types.size()>0 && key_types.contains(key_types)){
			
			userData = getMQ(code).get(curType);
			if(userData.keySet().contains(uId)){
				list_datas = userData.get(uId);
				if(null!=list_datas && list_datas.size()>=MESSAGE_UPLINE){
					
					Map<String,String> json_datas = jsonStr2Map(list_datas.get(0));
					if(Integer.valueOf(json_datas.get("mark"))==MESSAGE_MARK_NOSPE){
						
						list_datas.remove(0);	//移除第一个
						userData.remove(uId); 	//移除当前用户的消息
						userData.put(uId, list_datas);  //重新将当前用户消息添加进去
						
						getMQ(code).remove(curType);
						getMQ(code).put(curType,userData);
					}
				}
			}
		}
	}
	
	public static String removeHeader_end(String str){
		logger.info(getFormateDate()+"Netty服务器对当前消息进行格式化处理");
		
		if(null!=str && !"".equals(str)){
			if(str.startsWith("["))
				str = str.substring(1);
			if(str.endsWith("]"))
				str = str.substring(0,str.length()-1);
		}
		return str;
	}
	
	public static String json(String req){
		if(null!=req && !"".equals(req)){
			if(!req.startsWith("{"))
				req = "{"+req;
			if(!req.endsWith("}"))
				req = req + "}";
		}
		return req;
	}
	
	public static String addMark(String jsonData, int mark){
		if(null!=jsonData && jsonData.endsWith("}")){
			jsonData = jsonData.substring(0,jsonData.length()-1)+",\"mark\":\""+mark+"\"}";
		}
		return jsonData;
	}
	
	public static String addType(String jsonData, String type){
		if(null!=jsonData && jsonData.endsWith("}")){
			jsonData = jsonData.substring(0,jsonData.length()-1)+",\"type\":\""+type+"\"}";
		}
		return jsonData;
	}
	
	public static long getTimeStampByInfo(String info){
		Map<String,String> map = jsonStr2Map(info);
		return Long.valueOf(map.get("timeStamp"));
	}
	
	public static List<String> orderList(List<String> jsonInfos){
		
		List<Map<String,String>> messages = new ArrayList<Map<String,String>>();
		for (String info : jsonInfos) {
			messages.add(jsonStr2Map(info));
		}
		
        for (int i = 0; i < messages.size(); i++) {
            for (int j = i; j < messages.size(); j++) {
            	long time_i = Long.valueOf(messages.get(i).get("timeStamp"));
                long time_j = Long.valueOf(messages.get(j).get("timeStamp"));
 
                if (time_i > time_j) {
                	Map<String, String> tmp = messages.get(i);
                	messages.set(i, messages.get(j));
                	messages.set(j, tmp);
                }
            }
        }
        
        jsonInfos.clear();
        for (Map<String, String> map : messages) {
        	jsonInfos.add(JSONObject.fromObject(map).toString());
		}
        return jsonInfos;
	}
	
}
