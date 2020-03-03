package com.atguigu.gmall.sms.service.impl;

import com.atguigu.gmall.sms.dao.SkuFullReductionDao;
import com.atguigu.gmall.sms.dao.SkuLadderDao;
import com.atguigu.gmall.sms.entity.SkuFullReductionEntity;
import com.atguigu.gmall.sms.entity.SkuLadderEntity;
import com.atguigu.gmall.sms.vo.ItemSaleVO;
import com.atguigu.gmall.sms.vo.SkuSaleVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.core.bean.PageVo;
import com.atguigu.core.bean.Query;
import com.atguigu.core.bean.QueryCondition;

import com.atguigu.gmall.sms.dao.SkuBoundsDao;
import com.atguigu.gmall.sms.entity.SkuBoundsEntity;
import com.atguigu.gmall.sms.service.SkuBoundsService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;


@Service("skuBoundsService")
public class SkuBoundsServiceImpl extends ServiceImpl<SkuBoundsDao, SkuBoundsEntity> implements SkuBoundsService {

    @Autowired
    private SkuFullReductionDao reductionDao;

    @Autowired
    private SkuLadderDao ladderDao;

    @Override
    public PageVo queryPage(QueryCondition params) {
        IPage<SkuBoundsEntity> page = this.page(
                new Query<SkuBoundsEntity>().getPage(params),
                new QueryWrapper<SkuBoundsEntity>()
        );

        return new PageVo(page);
    }

    @Transactional
    @Override
    public void saveSkuSales(SkuSaleVO skuSaleVO) {
        Long skuId = skuSaleVO.getSkuId();
        // 3.1. 保存积分信息skuBounds
        SkuBoundsEntity skuBoundsEntity = new SkuBoundsEntity();
        skuBoundsEntity.setSkuId(skuId);
        skuBoundsEntity.setBuyBounds(skuSaleVO.getBuyBounds());
        skuBoundsEntity.setGrowBounds(skuSaleVO.getGrowBounds());
        List<Integer> works = skuSaleVO.getWork();
        // 保存叠加信息时，转化成10进制保存；将来查询时，再转化成2进制展示
        if (!CollectionUtils.isEmpty(works)){
            skuBoundsEntity.setWork(works.get(3) * 8 + works.get(2) * 4 + works.get(1) * 2 + works.get(0) * 1);
        }
        this.save(skuBoundsEntity);

        // 3.2. 保存满减信息skuFullReduction
        SkuFullReductionEntity reductionEntity = new SkuFullReductionEntity();
        reductionEntity.setFullPrice(skuSaleVO.getFullPrice());
        reductionEntity.setReducePrice(skuSaleVO.getReducePrice());
        reductionEntity.setAddOther(skuSaleVO.getFullAddOther());
        reductionEntity.setSkuId(skuId);
        this.reductionDao.insert(reductionEntity);

        // 3.3. 保存打折信息skuLadder
        SkuLadderEntity ladderEntity = new SkuLadderEntity();
        ladderEntity.setSkuId(skuId);
        ladderEntity.setFullCount(skuSaleVO.getFullCount());
        ladderEntity.setDiscount(skuSaleVO.getDiscount());
        ladderEntity.setAddOther(skuSaleVO.getLadderAddOther());
        this.ladderDao.insert(ladderEntity);
    }

    @Override
    public List<ItemSaleVO> queryItemSaleBySkuId(Long skuId) {
        List<ItemSaleVO> itemSaleVOS = new ArrayList<>();
        // 查询bounds
        SkuBoundsEntity skuBoundsEntity = this.getOne(new QueryWrapper<SkuBoundsEntity>().eq("sku_id", skuId));
        if (skuBoundsEntity != null) {
            ItemSaleVO itemSaleVO = new ItemSaleVO();
            itemSaleVO.setType("积分");
            itemSaleVO.setDesc("成长积分赠送" + skuBoundsEntity.getBuyBounds() + ",购物积分赠送" + skuBoundsEntity.getBuyBounds());
            itemSaleVOS.add(itemSaleVO);
        }

        // 查询满减信息
        SkuFullReductionEntity fullReductionEntity = this.reductionDao.selectOne(new QueryWrapper<SkuFullReductionEntity>().eq("sku_id", skuId));
        if (fullReductionEntity != null) {
            ItemSaleVO itemSaleVO = new ItemSaleVO();
            itemSaleVO.setType("满减");
            itemSaleVO.setDesc("满" + fullReductionEntity.getFullPrice() + "减" + fullReductionEntity.getReducePrice());
            itemSaleVOS.add(itemSaleVO);
        }

        // 查询打折信息
        SkuLadderEntity ladderEntity = this.ladderDao.selectOne(new QueryWrapper<SkuLadderEntity>().eq("sku_id", skuId));
        if (ladderEntity != null) {
            ItemSaleVO itemSaleVO = new ItemSaleVO();
            itemSaleVO.setType("打折");
            itemSaleVO.setDesc("满" + ladderEntity.getFullCount() + "件打" + ladderEntity.getDiscount().divide(new BigDecimal(10)) + "折");
            itemSaleVOS.add(itemSaleVO);
        }

        return itemSaleVOS;
    }

}