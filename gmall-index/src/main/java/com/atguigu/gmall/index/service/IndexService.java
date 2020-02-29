package com.atguigu.gmall.index.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.core.bean.Resp;
import com.atguigu.gmall.index.feign.GmallPmsClient;
import com.atguigu.gmall.pms.vo.CategoryVO;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class IndexService {

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "index:cates:";

    public List<CategoryEntity> queryLvl1Category() {

        Resp<List<CategoryEntity>> categoriesResp = this.pmsClient.queryCategoriesByLevelOrPid(1, null);

        return categoriesResp.getData();
    }

    public List<CategoryVO> queryLvl2WithSubByPid(Long pid) {
        // 先查询缓存，缓存中有直接命中返回
        String json = this.redisTemplate.opsForValue().get(KEY_PREFIX + pid);
        if (StringUtils.isNotBlank(json)){
            return JSON.parseArray(json, CategoryVO.class);
        }

        // 缓存中没有，查询数据库并放入缓存
        Resp<List<CategoryVO>> listResp = this.pmsClient.queryCategoryWithSubByPid(pid);
        List<CategoryVO> categoryVOS = listResp.getData();

        // 判断返回数据是否为空
        if (!CollectionUtils.isEmpty(categoryVOS)) {
            this.redisTemplate.opsForValue().set(KEY_PREFIX + pid, JSON.toJSONString(categoryVOS), 10, TimeUnit.DAYS);
        }

        return categoryVOS;
    }
}
