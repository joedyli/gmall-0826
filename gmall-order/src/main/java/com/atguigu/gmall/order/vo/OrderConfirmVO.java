package com.atguigu.gmall.order.vo;

import com.atguigu.gmall.oms.vo.OrderItemVO;
import com.atguigu.gmall.ums.entity.MemberReceiveAddressEntity;
import lombok.Data;

import java.util.List;

@Data
public class OrderConfirmVO {

    // 收货地址列表
    private List<MemberReceiveAddressEntity> addresses;

    // 送货清单
    private List<OrderItemVO> items;

    // 积分
    private Integer bounds; // 积分信息

    private String orderToken; // 订单结算页/确认页的唯一标识，防止重复提交
}
