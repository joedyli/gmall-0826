package com.atguigu.gmall.pms.service.impl;

import com.atguigu.gmall.pms.dao.*;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.feign.GmallSmsClient;
import com.atguigu.gmall.pms.service.SpuInfoDescService;
import com.atguigu.gmall.pms.vo.BaseAttrValueVO;
import com.atguigu.gmall.pms.vo.SkuInfoVO;
import com.atguigu.gmall.pms.vo.SpuInfoVO;
import com.atguigu.gmall.sms.vo.SkuSaleVO;
import io.seata.spring.annotation.GlobalTransactional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.core.bean.PageVo;
import com.atguigu.core.bean.Query;
import com.atguigu.core.bean.QueryCondition;

import com.atguigu.gmall.pms.service.SpuInfoService;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;


@Service("spuInfoService")
public class SpuInfoServiceImpl extends ServiceImpl<SpuInfoDao, SpuInfoEntity> implements SpuInfoService {

    @Autowired
    private SpuInfoDescDao descDao;

    @Autowired
    private ProductAttrValueDao baseAttrDao;

    @Autowired
    private SkuInfoDao skuInfoDao;

    @Autowired
    private SkuImagesDao imagesDao;

    @Autowired
    private SkuSaleAttrValueDao saleAttrDao;

    @Autowired
    private GmallSmsClient smsClient;

    @Autowired
    private SpuInfoDescService descService;

    @Override
    public PageVo queryPage(QueryCondition params) {
        IPage<SpuInfoEntity> page = this.page(
                new Query<SpuInfoEntity>().getPage(params),
                new QueryWrapper<SpuInfoEntity>()
        );

        return new PageVo(page);
    }

    @Override
    public PageVo querySpuByCidPage(QueryCondition condition, Long cid) {

        QueryWrapper<SpuInfoEntity> wrapper = new QueryWrapper<>();

        // 分类id判断
        if (cid != 0) {
            wrapper.eq("catalog_id", cid);
        }

        // 关键字判断
        String key = condition.getKey();
        if (StringUtils.isNotBlank(key)) {
            wrapper.and(t -> t.eq("id", key).or().like("spu_name", key));
        }

        IPage<SpuInfoEntity> page = this.page(new Query<SpuInfoEntity>().getPage(condition), wrapper);

        return new PageVo(page);
    }

    @GlobalTransactional
    @Override
    public void bigSave(SpuInfoVO spuInfoVO) throws FileNotFoundException {

        // 1.保存spu相关的信息（spuInfo spuInfoDesc productAttrValue）
        // 1.1.保存spuInfo信息
        Long spuId = saveSpuInfo(spuInfoVO);

        // 1.2.保存spuInfoDesc信息
        this.descService.saveSpuInfoDesc(spuInfoVO, spuId);  // requires_new 不会回滚

        // 1.3.保存基本属性（productAttrValue）
        saveBaseAttrValue(spuInfoVO, spuId);

//        try {
//            TimeUnit.SECONDS.sleep(4);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }

        // 2.保存sku相关的信息（需要spuId）
        saveSku(spuInfoVO, spuId);

//        int i = 1 / 0;
//        FileInputStream stream = new FileInputStream("xxx");
    }

    private void saveSku(SpuInfoVO spuInfoVO, Long spuId) {
        List<SkuInfoVO> skus = spuInfoVO.getSkus();
        if (CollectionUtils.isEmpty(skus)){
            return;
        }
        skus.forEach(skuInfoVO -> {
            // 2.1. 保存skuInfo
            skuInfoVO.setSpuId(spuId);
            skuInfoVO.setSkuCode(UUID.randomUUID().toString());
            skuInfoVO.setCatalogId(spuInfoVO.getCatalogId());
            skuInfoVO.setBrandId(spuInfoVO.getBrandId());
            // 设置默认图片，如果页面传了默认图片，使用页面传的默认图片，否则取第一张图片作为默认图片
            List<String> images = skuInfoVO.getImages();
            if (!CollectionUtils.isEmpty(images)){
                skuInfoVO.setSkuDefaultImg(StringUtils.isNotBlank(skuInfoVO.getSkuDefaultImg()) ? skuInfoVO.getSkuDefaultImg() : images.get(0));
            }
            this.skuInfoDao.insert(skuInfoVO);
            Long skuId = skuInfoVO.getSkuId();

            // 2.2. 保存sku图片信息skuImages
            if (!CollectionUtils.isEmpty(images)){
                images.forEach(image -> {
                    SkuImagesEntity imagesEntity = new SkuImagesEntity();
                    imagesEntity.setImgUrl(image);
                    imagesEntity.setSkuId(skuId);
                    if (StringUtils.equals(image, skuInfoVO.getSkuDefaultImg())){
                        imagesEntity.setDefaultImg(1);
                    } else {
                        imagesEntity.setDefaultImg(0);
                    }
                    imagesDao.insert(imagesEntity);
                });
            }

            // 2.3. 保存销售属性skuSaleAttrValue
            List<SkuSaleAttrValueEntity> saleAttrs = skuInfoVO.getSaleAttrs();
            if (!CollectionUtils.isEmpty(saleAttrs)){
                saleAttrs.forEach(skuSaleAttrValueEntity -> {
                    skuSaleAttrValueEntity.setSkuId(skuId);
                    this.saleAttrDao.insert(skuSaleAttrValueEntity);
                });
            }

            // 3.保存sku营销相关信息(需要skuId)
            SkuSaleVO skuSaleVO = new SkuSaleVO();
            BeanUtils.copyProperties(skuInfoVO, skuSaleVO);
            this.smsClient.saveSkuSales(skuSaleVO);
        });
    }

    private void saveBaseAttrValue(SpuInfoVO spuInfoVO, Long spuId) {
        List<BaseAttrValueVO> baseAttrs = spuInfoVO.getBaseAttrs();
        if (!CollectionUtils.isEmpty(baseAttrs)) {
            baseAttrs.forEach(baseAttrValueVO -> {
                baseAttrValueVO.setSpuId(spuId);
                this.baseAttrDao.insert(baseAttrValueVO);
            });
        }
    }

    private Long saveSpuInfo(SpuInfoVO spuInfoVO) {
        spuInfoVO.setCreateTime(new Date());
        spuInfoVO.setUodateTime(spuInfoVO.getCreateTime());
        this.save(spuInfoVO);
        return spuInfoVO.getId();
    }

}