package com.atguigu.gmall.order.vo;

import com.atguigu.gmall.pms.entity.SkuSaleAttrValueEntity;
import com.atguigu.gmall.sms.vo.ItemSaleVO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderItemVO {

    private Long skuId;
    private String image;
    private String title;
    private List<SkuSaleAttrValueEntity> saleAttrs; // 销售属性
    private BigDecimal price;
    private BigDecimal count;
    private List<ItemSaleVO> sales; // 营销信息
    private BigDecimal weight; // 重量

    private Boolean store; // 是否有货
}
