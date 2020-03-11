package com.atguigu.gmall.oms.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.core.bean.Resp;
import com.atguigu.gmall.oms.dao.OrderItemDao;
import com.atguigu.gmall.oms.entity.OrderItemEntity;
import com.atguigu.gmall.oms.feign.GmallPmsClient;
import com.atguigu.gmall.oms.feign.GmallUmsClient;
import com.atguigu.gmall.oms.vo.OrderItemVO;
import com.atguigu.gmall.oms.vo.OrderSubmitVO;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.ums.entity.MemberEntity;
import com.atguigu.gmall.ums.entity.MemberReceiveAddressEntity;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.core.bean.PageVo;
import com.atguigu.core.bean.Query;
import com.atguigu.core.bean.QueryCondition;

import com.atguigu.gmall.oms.dao.OrderDao;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.oms.service.OrderService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;


@Service("orderService")
public class OrderServiceImpl extends ServiceImpl<OrderDao, OrderEntity> implements OrderService {

    @Autowired
    private OrderItemDao itemDao;

    @Autowired
    private GmallUmsClient umsClient;

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private AmqpTemplate amqpTemplate;

    @Override
    public PageVo queryPage(QueryCondition params) {
        IPage<OrderEntity> page = this.page(
                new Query<OrderEntity>().getPage(params),
                new QueryWrapper<OrderEntity>()
        );

        return new PageVo(page);
    }

    @Transactional
    @Override
    public OrderEntity saveOrder(OrderSubmitVO submitVO) {
        // 保存订单
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setMemberId(submitVO.getUserId());
        orderEntity.setOrderSn(submitVO.getOrderToken());
        orderEntity.setCreateTime(new Date());
        Resp<MemberEntity> memberEntityResp = this.umsClient.queryMemberById(submitVO.getUserId());
        MemberEntity memberEntity = memberEntityResp.getData();
        orderEntity.setMemberUsername(memberEntity.getUsername());
        orderEntity.setTotalAmount(submitVO.getTotalPrice());
        orderEntity.setPayType(submitVO.getPayType());
        orderEntity.setSourceType(0);
        orderEntity.setStatus(0);// 未付款
        orderEntity.setDeliveryCompany(submitVO.getDeliveryCompany());
        // TODO：成长积分和购物积分，需要远程调用sms的接口，查询该订单

        MemberReceiveAddressEntity address = submitVO.getAddress();
        orderEntity.setReceiverRegion(address.getRegion());
        orderEntity.setReceiverProvince(address.getProvince());
        orderEntity.setReceiverPostCode(address.getPostCode());
        orderEntity.setReceiverPhone(address.getPhone());
        orderEntity.setReceiverName(address.getName());
        orderEntity.setReceiverDetailAddress(address.getDetailAddress());
        orderEntity.setReceiverCity(address.getCity());
        orderEntity.setUseIntegration(submitVO.getBounds());
        orderEntity.setDeleteStatus(0);
        this.save(orderEntity);

        // 保存订单详情
        List<OrderItemVO> items = submitVO.getItems();
        if (!CollectionUtils.isEmpty(items)) {
            items.forEach(item -> {
                OrderItemEntity itemEntity = new OrderItemEntity();
                itemEntity.setOrderId(orderEntity.getId());
                itemEntity.setOrderSn(submitVO.getOrderToken());
                // 查询sku信息
                Resp<SkuInfoEntity> skuInfoEntityResp = this.pmsClient.querySkuById(item.getSkuId());
                SkuInfoEntity skuInfoEntity = skuInfoEntityResp.getData();
                itemEntity.setSkuId(item.getSkuId());
                itemEntity.setSkuPrice(skuInfoEntity.getPrice());
                itemEntity.setSkuPic(skuInfoEntity.getSkuDefaultImg());
                itemEntity.setSkuQuantity(item.getCount().intValue());
                itemEntity.setSkuName(skuInfoEntity.getSkuName());
                // 查询销售属性
                Resp<List<SkuSaleAttrValueEntity>> listResp = this.pmsClient.querySaleAttrBySkuId(item.getSkuId());
                List<SkuSaleAttrValueEntity> attrValueEntities = listResp.getData();
                itemEntity.setSkuAttrsVals(JSON.toJSONString(attrValueEntities));
                // 查询spu信息
                Resp<SpuInfoEntity> spuInfoEntityResp = this.pmsClient.querySpuById(skuInfoEntity.getSpuId());
                SpuInfoEntity spuInfoEntity = spuInfoEntityResp.getData();
                itemEntity.setSpuId(skuInfoEntity.getSpuId());
                itemEntity.setSpuName(spuInfoEntity.getSpuName());
                itemEntity.setCategoryId(spuInfoEntity.getCatalogId());
                // 查询spu的描述信息
                Resp<SpuInfoDescEntity> spuInfoDescEntityResp = this.pmsClient.querySpuDescBySpuId(spuInfoEntity.getId());
                SpuInfoDescEntity spuInfoDescEntity = spuInfoDescEntityResp.getData();
                itemEntity.setSpuPic(spuInfoDescEntity.getDecript());
                // 查询品牌的名称
                Resp<BrandEntity> brandEntityResp = this.pmsClient.queryBrandById(skuInfoEntity.getBrandId());
                BrandEntity brandEntity = brandEntityResp.getData();
                itemEntity.setSpuBrand(brandEntity.getName());

                this.itemDao.insert(itemEntity);
            });
        }

        // 订单创建成功之后立马定时关单
        this.amqpTemplate.convertAndSend("ORDER-EXCHANGE", "order.ttl", submitVO.getOrderToken());

        return null;
    }

}