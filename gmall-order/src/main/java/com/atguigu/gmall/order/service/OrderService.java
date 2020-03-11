package com.atguigu.gmall.order.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.core.bean.Resp;
import com.atguigu.core.bean.UserInfo;
import com.atguigu.core.exception.OrderException;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.order.feign.*;
import com.atguigu.gmall.order.interceptor.LoginInterceptor;
import com.atguigu.gmall.order.vo.OrderConfirmVO;
import com.atguigu.gmall.oms.vo.OrderItemVO;
import com.atguigu.gmall.oms.vo.OrderSubmitVO;
import com.atguigu.gmall.pms.entity.SkuInfoEntity;
import com.atguigu.gmall.pms.entity.SkuSaleAttrValueEntity;
import com.atguigu.gmall.sms.vo.ItemSaleVO;
import com.atguigu.gmall.ums.entity.MemberEntity;
import com.atguigu.gmall.ums.entity.MemberReceiveAddressEntity;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.atguigu.gmall.wms.vo.SkuLockVO;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class OrderService {

    @Autowired
    private GmallCartClient cartClient;

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallSmsClient smsClient;

    @Autowired
    private GmallWmsClient wmsClient;

    @Autowired
    private GmallUmsClient umsClient;

    @Autowired
    private GmallOmsClient omsClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "order:token:";

    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    @Autowired
    private AmqpTemplate amqpTemplate;

    /**
     * 订单确认页
     * 由于存在大量的远程调用，这里使用异步编排做优化
     * @return
     */
    public OrderConfirmVO confirm() {
        OrderConfirmVO orderConfirmVO = new OrderConfirmVO();

        // 获取用户的扽牢固信息
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getUserId();
        if(userId == null){
            throw new OrderException("用户登录已过期！");
        }

        // 订单的详情列表
        // 远程调用购物车，查询购物车中的选中的记录
        CompletableFuture<Void> cartFuture = CompletableFuture.supplyAsync(() -> {
            Resp<List<Cart>> checkedCartsResp = this.cartClient.queryCheckedCarts(userId);
            List<Cart> carts = checkedCartsResp.getData();
            return carts;
        }, threadPoolExecutor).thenAcceptAsync(carts -> {
            List<OrderItemVO> items = carts.stream().map(cart -> { // skuId  count
                OrderItemVO orderItemVO = new OrderItemVO();
//            BeanUtils.copyProperties(cart, orderItemVO);
                // 为了保证数据实时同步，这里一定重新查询一次数据库
                CompletableFuture<SkuInfoEntity> skuFuture = CompletableFuture.supplyAsync(() -> {
                    Resp<SkuInfoEntity> skuInfoEntityResp = this.pmsClient.querySkuById(cart.getSkuId());
                    SkuInfoEntity skuInfoEntity = skuInfoEntityResp.getData();
                    if (skuInfoEntity != null) {
                        orderItemVO.setSkuId(cart.getSkuId());
                        orderItemVO.setTitle(skuInfoEntity.getSkuTitle());
                        orderItemVO.setPrice(skuInfoEntity.getPrice());
                        orderItemVO.setImage(skuInfoEntity.getSkuDefaultImg());
                        orderItemVO.setWeight(skuInfoEntity.getWeight());
                        orderItemVO.setCount(cart.getCount());
                    }
                    return skuInfoEntity;
                }, threadPoolExecutor);

                CompletableFuture<Void> saleAttrFuture = skuFuture.thenAcceptAsync(skuInfoEntity -> {
                    if (skuInfoEntity != null) {
                        // 查询销售属性
                        Resp<List<SkuSaleAttrValueEntity>> saleAttrResp = this.pmsClient.querySaleAttrBySkuId(cart.getSkuId());
                        List<SkuSaleAttrValueEntity> saleAttrs = saleAttrResp.getData();
                        orderItemVO.setSaleAttrs(saleAttrs);
                    }
                }, threadPoolExecutor);
                CompletableFuture<Void> salesFuture = skuFuture.thenAcceptAsync(skuInfoEntity -> {
                    if (skuInfoEntity != null) {
                        // 查询营销信息
                        Resp<List<ItemSaleVO>> saleResp = this.smsClient.queryItemSaleBySkuId(cart.getSkuId());
                        List<ItemSaleVO> itemSaleVOList = saleResp.getData();
                        orderItemVO.setSales(itemSaleVOList);
                    }
                }, threadPoolExecutor);

                CompletableFuture<Void> storeFuture = skuFuture.thenAcceptAsync(skuInfoEntity -> {
                    if (skuInfoEntity != null) {
                        // 是否有货
                        Resp<List<WareSkuEntity>> wareResp = this.wmsClient.queryWareSkusBySkuId(cart.getSkuId());
                        List<WareSkuEntity> wareSkuEntities = wareResp.getData();
                        if (!CollectionUtils.isEmpty(wareSkuEntities)) {
                            orderItemVO.setStore(wareSkuEntities.stream().anyMatch(ware -> ware.getStock() > 0));
                        }
                    }
                }, threadPoolExecutor);
                CompletableFuture.allOf(saleAttrFuture, salesFuture, storeFuture).join();
                return orderItemVO;
            }).collect(Collectors.toList());
            orderConfirmVO.setItems(items);
        }, threadPoolExecutor);

        // 收获地址列表
        CompletableFuture<Void> addressFuture = CompletableFuture.runAsync(() -> {
            Resp<List<MemberReceiveAddressEntity>> addressesResp = this.umsClient.queryAddressesByUserId(userId);
            List<MemberReceiveAddressEntity> addresses = addressesResp.getData();
            orderConfirmVO.setAddresses(addresses);
        }, threadPoolExecutor);

        // 用户可用积分
        CompletableFuture<Void> boundsFuture = CompletableFuture.runAsync(() -> {
            Resp<MemberEntity> memberEntityResp = this.umsClient.queryMemberById(userId);
            MemberEntity memberEntity = memberEntityResp.getData();
            if (memberEntity != null) {
                orderConfirmVO.setBounds(memberEntity.getIntegration());
            }
        }, threadPoolExecutor);

        // 防止重复提交的唯一标识
        CompletableFuture<Void> tokenFuture = CompletableFuture.runAsync(() -> {
            String orderToken = IdWorker.getTimeId();
            orderConfirmVO.setOrderToken(orderToken);
            this.redisTemplate.opsForValue().set(KEY_PREFIX + orderToken, orderToken, 3, TimeUnit.HOURS);
        }, threadPoolExecutor);

        CompletableFuture.allOf(cartFuture, addressFuture, boundsFuture, tokenFuture).join();

        return orderConfirmVO;
    }

    public void submit(OrderSubmitVO submitVO) {

        // 1.防重
        String orderToken = submitVO.getOrderToken();
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] " +
                "then return redis.call('del', KEYS[1]) " +
                "else return 0 end";
        Boolean flag = this.redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Arrays.asList(KEY_PREFIX + orderToken), orderToken);
        if (!flag) {
            throw new OrderException("您多次提交过快，请稍后再试！");
        }

        // 2.验价
        BigDecimal totalPrice = submitVO.getTotalPrice(); // 获取页面上的价格
        List<OrderItemVO> items = submitVO.getItems(); // 订单详情
        if (CollectionUtils.isEmpty(items)) {
            throw new OrderException("您没有选中的商品，请选择要购买的商品！");
        }
        // 遍历订单详情，获取数据库价格，计算实时总价
        BigDecimal currentTotalPrice = items.stream().map(item -> {
            Resp<SkuInfoEntity> skuInfoEntityResp = this.pmsClient.querySkuById(item.getSkuId());
            SkuInfoEntity skuInfoEntity = skuInfoEntityResp.getData();
            if (skuInfoEntity != null) {
                return skuInfoEntity.getPrice().multiply(item.getCount());
            }
            return new BigDecimal(0);
        }).reduce((t1, t2) -> t1.add(t2)).get();
        if (totalPrice.compareTo(currentTotalPrice) != 0) {
            throw new OrderException("页面已过期，刷新后再试！");
        }

        // 3.验库存并锁库存
        List<SkuLockVO> lockVOS = items.stream().map(item -> {
            SkuLockVO skuLockVO = new SkuLockVO();
            skuLockVO.setSkuId(item.getSkuId());
            skuLockVO.setCount(item.getCount().intValue());
            skuLockVO.setOrderToken(submitVO.getOrderToken());
            return skuLockVO;
        }).collect(Collectors.toList());
        Resp<List<SkuLockVO>> skuLockResp = this.wmsClient.checkAndLock(lockVOS);
        List<SkuLockVO> skuLockVOS = skuLockResp.getData();
        if (!CollectionUtils.isEmpty(skuLockVOS)){
            throw new OrderException("手慢了，商品库存不足：" + JSON.toJSONString(skuLockVOS));
        }

        // order：此时服务器宕机

        // 4.下单
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getUserId();
        submitVO.setUserId(userId);
        try {
            this.omsClient.saveOrder(submitVO); // feign（请求，响应）超时
        } catch (Exception e) {
            e.printStackTrace();
            // 如果订单创建失败，立马释放库存 TODO
            this.amqpTemplate.convertAndSend("ORDER-EXCHANGE", "wms.unlock", orderToken);
            // 如果订单创建成功，响应失败，发送消息标记为无效订单
            this.amqpTemplate.convertAndSend("ORDER-EXCHANGE", "oms.close", orderToken);
        }

        // 5.删除购物车。异步发送消息给购物车，删除购物车
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userId);
        List<Long> skuIds = items.stream().map(OrderItemVO::getSkuId).collect(Collectors.toList());
        map.put("skuIds", JSON.toJSONString(skuIds));
        this.amqpTemplate.convertAndSend("ORDER-EXCHANGE", "cart.delete", map);
    }

}
