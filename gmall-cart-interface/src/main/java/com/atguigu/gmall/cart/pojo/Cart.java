package com.atguigu.gmall.cart.pojo;

import com.atguigu.gmall.pms.entity.SkuSaleAttrValueEntity;
import com.atguigu.gmall.sms.vo.ItemSaleVO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class Cart {

    private Long skuId;
    private Boolean check; // 选中状态
    private String image;
    private String title;
    private List<SkuSaleAttrValueEntity> saleAttrs; // 销售属性
    private BigDecimal price; // 加入购物车时的价格
    private BigDecimal currentPrice; // 实时价格
    private BigDecimal count;
    private List<ItemSaleVO> sales; // 营销信息
}
