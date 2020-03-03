package com.atguigu.gmall.sms.vo;

import lombok.Data;

@Data
public class ItemSaleVO {

    private String type; // 促销类型：积分 打折 满减

    private String desc; // 营销的详细信息
}
