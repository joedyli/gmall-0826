package com.atguigu.gmall.order.vo;

import com.atguigu.gmall.ums.entity.MemberReceiveAddressEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderSubmitVO {

    private String orderToken; // 防重
    private BigDecimal totalPrice; // 总价，校验价格变化
    private MemberReceiveAddressEntity address; // 收货人信息
    private Integer payType; // 支付方式
    private String deliveryCompany; // 配送方式
    private List<OrderItemVO> items; // 订单详情信息
    private Integer bounds; // 积分信息
}
