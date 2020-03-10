package com.atguigu.gmall.cart.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.core.bean.Resp;
import com.atguigu.gmall.cart.feign.GmallPmsClient;
import com.atguigu.gmall.cart.feign.GmallSmsClient;
import com.atguigu.gmall.cart.feign.GmallWmsClient;
import com.atguigu.gmall.cart.interceptor.LoginInterceptor;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.core.bean.UserInfo;
import com.atguigu.gmall.pms.entity.SkuInfoEntity;
import com.atguigu.gmall.pms.entity.SkuSaleAttrValueEntity;
import com.atguigu.gmall.sms.vo.ItemSaleVO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CartService {

    private static final String KEY_PREFIX = "cart:";

    private static final String PRICE_PREFIX = "cart:price:";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallSmsClient smsClient;

    @Autowired
    private GmallWmsClient wmsClient;

    public void addCart(Cart cart) { // skuId count

        // 获取用户信息，判断登录状态
        String key = this.generatedKey();

        // 获取该用户的购物车操作对象
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(key);

        String skuId = cart.getSkuId().toString();
        BigDecimal count = cart.getCount();
        // 判断购物车中有没有该商品
        if (hashOps.hasKey(skuId)) {
            // 有，更新数量
            String cartJson = hashOps.get(skuId).toString();
            cart = JSON.parseObject(cartJson, Cart.class);
            cart.setCount(cart.getCount().add(count));
        } else {
            // 没有新增
            Resp<SkuInfoEntity> skuInfoEntityResp = this.pmsClient.querySkuById(cart.getSkuId());
            SkuInfoEntity skuInfoEntity = skuInfoEntityResp.getData();
            cart.setImage(skuInfoEntity.getSkuDefaultImg());
            cart.setPrice(skuInfoEntity.getPrice());
//            cart.setCurrentPrice(skuInfoEntity.getPrice());
            cart.setTitle(skuInfoEntity.getSkuTitle());
            cart.setCheck(true);
            // 营销信息
            Resp<List<ItemSaleVO>> itemSaleResp = this.smsClient.queryItemSaleBySkuId(cart.getSkuId());
            List<ItemSaleVO> itemSaleVOS = itemSaleResp.getData();
            cart.setSales(itemSaleVOS);
            Resp<List<SkuSaleAttrValueEntity>> listResp = this.pmsClient.querySaleAttrBySkuId(cart.getSkuId());
            List<SkuSaleAttrValueEntity> skuSaleAttrValueEntities = listResp.getData();
            cart.setSaleAttrs(skuSaleAttrValueEntities);

            this.redisTemplate.opsForValue().set(PRICE_PREFIX + skuId, skuInfoEntity.getPrice().toString());
        }
        hashOps.put(skuId, JSON.toJSONString(cart));
    }

    public List<Cart> queryCarts() {
        // 获取登录信息
        UserInfo userInfo = LoginInterceptor.getUserInfo();

        // 1.以userKey作为key查询未登录的购物车
        String unLoginKey = KEY_PREFIX + userInfo.getUserKey();
        BoundHashOperations<String, Object, Object> unLoginOps = this.redisTemplate.boundHashOps(unLoginKey);
        List<Object> cartJsons = unLoginOps.values();
        List<Cart> unLoginCarts = null;
        if (!CollectionUtils.isEmpty(cartJsons)) {
            unLoginCarts = cartJsons.stream().map(cartJson -> {
                Cart cart = JSON.parseObject(cartJson.toString(), Cart.class);
                // 从缓存中查询最新价格
                String price = this.redisTemplate.opsForValue().get(PRICE_PREFIX + cart.getSkuId());
                cart.setCurrentPrice(new BigDecimal(price));
                return cart;
            }).collect(Collectors.toList());
        }

        // 2.判断登录状态，未登录直接返回
        Long userId = userInfo.getUserId();
        if (userId == null) {
            return unLoginCarts;
        }

        String loginKey = KEY_PREFIX + userId;
        // 3.登陆了，遍历未登录的购物车，合并到已登录的购物车
        BoundHashOperations<String, Object, Object> loginOps = this.redisTemplate.boundHashOps(loginKey);
        if (!CollectionUtils.isEmpty(unLoginCarts)){
            unLoginCarts.forEach(cart -> {
                // 判断这个未登陆的额购物车，在登录购物车中是否存在
                if (loginOps.hasKey(cart.getSkuId().toString())){
                    // 存在，更新数量
                    String cartJson = loginOps.get(cart.getSkuId().toString()).toString();
                    BigDecimal count = cart.getCount();// 未登录购物车数量
                    cart = JSON.parseObject(cartJson, Cart.class);
                    cart.setCount(cart.getCount().add(count));
                }
                loginOps.put(cart.getSkuId().toString(), JSON.toJSONString(cart));
            });
        }

        // 删除未登录的购物车
        this.redisTemplate.delete(unLoginKey);

        // 4.查询已登录的购物车信息
        List<Object> loginCartJsons = loginOps.values();
        if (!CollectionUtils.isEmpty(loginCartJsons)){
            return loginCartJsons.stream().map(cartJson -> {
                Cart cart = JSON.parseObject(cartJson.toString(), Cart.class);
                // 查询最新价格
                cart.setCurrentPrice(new BigDecimal(this.redisTemplate.opsForValue().get(PRICE_PREFIX + cart.getSkuId())));
                return cart;
            }).collect(Collectors.toList());
        }
        return null;
    }

    public void updateNum(Cart cart) {
        // 获取用户的登录信息
        String key = generatedKey();

        // 获取购物车的操作对象
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(key);

        // 更新数量
        String cartJson = hashOps.get(cart.getSkuId().toString()).toString();
        if (StringUtils.isNotBlank(cartJson)){
            BigDecimal count = cart.getCount();
            cart = JSON.parseObject(cartJson, Cart.class);
            cart.setCount(count);
            hashOps.put(cart.getSkuId().toString(), JSON.toJSONString(cart));
        }
    }

    public void updateCheck(Cart cart) {
        String key = generatedKey();

        // 获取购物车的操作对象
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(key);

        // 更新数量
        String cartJson = hashOps.get(cart.getSkuId().toString()).toString();
        if (StringUtils.isNotBlank(cartJson)){
            Boolean check = cart.getCheck();
            cart = JSON.parseObject(cartJson, Cart.class);
            cart.setCheck(check);
            hashOps.put(cart.getSkuId().toString(), JSON.toJSONString(cart));
        }
    }

    public void deleteCart(Long skuId) {
        String key = this.generatedKey();

        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(key);
        hashOps.delete(skuId.toString());
    }

    private String generatedKey() {
        // 获取用户的登录信息
        UserInfo userInfo = LoginInterceptor.getUserInfo();

        // 判断登录状态，组装key
        String key = KEY_PREFIX;
        if (userInfo.getUserId() == null) {
            key += userInfo.getUserKey();
        } else {
            key += userInfo.getUserId();
        }
        return key;
    }

    public List<Cart> queryCheckedCarts(Long userId) {

        String key = KEY_PREFIX + userId;
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(key);
        List<Object> cartJsons = hashOps.values();
        if (CollectionUtils.isEmpty(cartJsons)) {
            return null;
        }
        return cartJsons.stream().map(cartJson -> JSON.parseObject(cartJson.toString(), Cart.class)).filter(cart -> cart.getCheck()).collect(Collectors.toList());
    }
}
