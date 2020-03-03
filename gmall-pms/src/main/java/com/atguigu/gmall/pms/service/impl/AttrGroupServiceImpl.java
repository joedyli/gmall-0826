package com.atguigu.gmall.pms.service.impl;

import com.atguigu.gmall.pms.dao.AttrAttrgroupRelationDao;
import com.atguigu.gmall.pms.dao.AttrDao;
import com.atguigu.gmall.pms.dao.ProductAttrValueDao;
import com.atguigu.gmall.pms.entity.AttrAttrgroupRelationEntity;
import com.atguigu.gmall.pms.entity.AttrEntity;
import com.atguigu.gmall.pms.entity.ProductAttrValueEntity;
import com.atguigu.gmall.pms.vo.GroupVO;
import com.atguigu.gmall.pms.vo.ItemGroupVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.core.bean.PageVo;
import com.atguigu.core.bean.Query;
import com.atguigu.core.bean.QueryCondition;

import com.atguigu.gmall.pms.dao.AttrGroupDao;
import com.atguigu.gmall.pms.entity.AttrGroupEntity;
import com.atguigu.gmall.pms.service.AttrGroupService;
import org.springframework.util.CollectionUtils;


@Service("attrGroupService")
public class AttrGroupServiceImpl extends ServiceImpl<AttrGroupDao, AttrGroupEntity> implements AttrGroupService {

    @Autowired
    private AttrAttrgroupRelationDao relationDao;

    @Autowired
    private AttrDao attrDao;

    @Autowired
    private ProductAttrValueDao attrValueDao;

    @Override
    public PageVo queryPage(QueryCondition params) {
        IPage<AttrGroupEntity> page = this.page(
                new Query<AttrGroupEntity>().getPage(params),
                new QueryWrapper<AttrGroupEntity>()
        );

        return new PageVo(page);
    }

    @Override
    public PageVo queryGroupsByCid(QueryCondition queryCondition, Long cid) {

        IPage<AttrGroupEntity> page = this.page(
                new Query<AttrGroupEntity>().getPage(queryCondition),
                new QueryWrapper<AttrGroupEntity>().eq("catelog_id", cid)
        );

        return new PageVo(page);
    }

    @Override
    public GroupVO queryGroupVOById(Long id) {

        GroupVO groupVO = new GroupVO();
        // 1.根据id查询分组
        AttrGroupEntity groupEntity = this.getById(id);
        BeanUtils.copyProperties(groupEntity, groupVO);

        // 2.根据分组id查询中间表
        List<AttrAttrgroupRelationEntity> attrgroupRelationEntities = this.relationDao.selectList(new QueryWrapper<AttrAttrgroupRelationEntity>().eq("attr_group_id", id));
        // 判断这个分组的中间表数据是否为空
        if (CollectionUtils.isEmpty(attrgroupRelationEntities)){
            return groupVO;
        }
        groupVO.setRelations(attrgroupRelationEntities);

        // 3.收集中间表中attrIds，查询规格参数 map(把一个集合处理称另外一个集合) filter（过滤） reduce（求总和）
        List<Long> ids = attrgroupRelationEntities.stream().map(AttrAttrgroupRelationEntity::getAttrId).collect(Collectors.toList());

//        List<Long> ids = new ArrayList<>();
//        for (AttrAttrgroupRelationEntity attrgroupRelationEntity : attrgroupRelationEntities) {
//            ids.add(attrgroupRelationEntity.getAttrId());
//        }
        List<AttrEntity> attrEntities = this.attrDao.selectBatchIds(ids);
        groupVO.setAttrEntities(attrEntities);

        return groupVO;
    }

    @Override
    public List<GroupVO> queryGroupWithAttrByCid(Long cid) {
        // 根据分类的id查询分组
        List<AttrGroupEntity> groupEntities = this.list(new QueryWrapper<AttrGroupEntity>().eq("catelog_id", cid));
        if (CollectionUtils.isEmpty(groupEntities)) {
            return null;
        }

        // 根据分组的id查询该组下的规格参数
        return groupEntities.stream().map(attrGroupEntity -> {
            GroupVO groupVO = this.queryGroupVOById(attrGroupEntity.getAttrGroupId());
            return groupVO;
        }).collect(Collectors.toList());
    }

    @Override
    public List<ItemGroupVO> queryItemGroupsBySpuIdAndCid(Long spuId, Long cid) {

        // 1.根据cid查询分组
        List<AttrGroupEntity> attrGroupEntities = this.list(new QueryWrapper<AttrGroupEntity>().eq("catelog_id", cid));

        if (CollectionUtils.isEmpty(attrGroupEntities)){
            return null;
        }

        // 2.遍历分组查询每个组下的attr
        return attrGroupEntities.stream().map(group -> {
            ItemGroupVO itemGroupVO = new ItemGroupVO();
            itemGroupVO.setGroupId(group.getAttrGroupId());
            itemGroupVO.setGroupName(group.getAttrGroupName());

            List<AttrAttrgroupRelationEntity> relationEntities = this.relationDao.selectList(new QueryWrapper<AttrAttrgroupRelationEntity>().eq("attr_group_id", group.getAttrGroupId()));
            if (!CollectionUtils.isEmpty(relationEntities)){
                List<Long> attrIds = relationEntities.stream().map(AttrAttrgroupRelationEntity::getAttrId).collect(Collectors.toList());
                // 3.attrId结合spuId查询规格参数对应值
                List<ProductAttrValueEntity> attrValueEntities = this.attrValueDao.selectList(new QueryWrapper<ProductAttrValueEntity>().eq("spu_id", spuId).in("attr_id", attrIds));
                itemGroupVO.setBaseAttrValues(attrValueEntities);
            }
            return itemGroupVO;
        }).collect(Collectors.toList());
    }

//    public static void main(String[] args) {
//        List<User> users = Arrays.asList(
//                new User(1l, "zhang3", 20),
//                new User(2l, "liuyan", 21),
//                new User(3l, "xiaolu", 22),
//                new User(4l, "fengjie", 23),
//                new User(5l, "yifei", 22)
//        );
//        List<User> users1 = users.stream().filter(user -> user.getAge() == 22).collect(Collectors.toList());
//        users1.forEach(System.out::println);
//
//        Integer totalAge = users.stream().map(User::getAge).reduce((age1, age2) -> age1 + age2).get();
//        System.out.println(totalAge);
//    }

}



@Data
@AllArgsConstructor
@NoArgsConstructor
class User{
    private Long id;
    private String name;
    private Integer age;
}