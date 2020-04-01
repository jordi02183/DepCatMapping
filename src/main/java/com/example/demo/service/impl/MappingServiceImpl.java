package com.example.demo.service.impl;

import com.example.demo.dao.CategoryDAO;
import com.example.demo.dao.RecruitDAO;
import com.example.demo.module.AhoCorasickModule;
import com.example.demo.module.KomoranModule;
import com.example.demo.module.RegulizationModule;
import com.example.demo.service.MappingService;
import com.example.demo.vo.CategoryVO;
import com.example.demo.vo.RecruitVO;
import com.google.common.collect.Lists;
import lombok.NonNull;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MappingServiceImpl implements MappingService {
    HashMap<String, CategoryVO> depth3_categoryVO;
    HashMap<String, CategoryVO> depth1_categoryVO;
    HashMap<String, CategoryVO> depth2_categoryVO;
    List<RecruitVO> recruitVO;
    List<RecruitVO> SingleDepart;
    List<RecruitVO> MultiDepart;
    HashMap<String,RecruitVO> TestSet;
    HashMap<String,Integer> allWordCount;
    KomoranModule komoran;
    AhoCorasickModule ahoCorasickModule;

    MappingServiceImpl(KomoranModule komoran, AhoCorasickModule ahoCorasickModule){
        depth3_categoryVO = CategoryDAO.txt_to_category("3");
        depth2_categoryVO = CategoryDAO.txt_to_category("2");
        depth1_categoryVO = CategoryDAO.txt_to_category("1");
        SingleDepart = new ArrayList<>();
        MultiDepart = new ArrayList<>();
        TestSet = new HashMap<>();
        recruitVO = RecruitDAO.textToRecruitVO();
        allWordCount = new HashMap<>();
        this.komoran = komoran;
        this.ahoCorasickModule = ahoCorasickModule;
        split_recruit_vo();
    }

    public void split_recruit_vo(){
        int x=0,y=0;
        for(RecruitVO vo: recruitVO) {
            String[] rec_div = vo.getRec_division().split(",", 100);
            String[] category = vo.getCat_key().split("\\|", 100);
            //모집분야가 한개이거나 직종이 한개일 경우 SingleDepart
            if (rec_div.length == 1 || category.length == 1) {
                SingleDepart.add(vo);
                x++;
            } else {
                MultiDepart.add(vo);
                TestSet.put(vo.getRec_idx(), vo);
                y++;
            }
        }
        System.out.println(x+","+y);
        //test set, 직종번호 확인용
    }

    /***단일 모집분야/단일 직종 or 단일 모집분야/다직종 or 다모집분야/단일직종 mapping***/
    @NonNull
    public void set_category_vo(){
        for(RecruitVO vo: SingleDepart){
            for(String rec:vo.getRec_division().split(",")) {
                if (rec.length() > 0) {
                List<String> related_words = komoran.Tokenizer(rec);
                for (String cat : vo.getCat_key().split("\\|")) {
                    CategoryVO tmpVO = depth3_categoryVO.get(cat);
                    if (tmpVO != null) {
                    HashMap<String, Integer> tmp = tmpVO.getRelated_words();
                    for (String word : related_words) {
                        if (!ahoCorasickModule.isHitKeyword(word)) {
                        //부서별 Regularization을 위한 부서별 count
                        tmpVO.setRelated_words_count(tmpVO.getRelated_words_count() + 1);

                        word = word.toLowerCase();
                        if (tmp.containsKey(word)) tmp.put(word, tmp.get(word) + 1);
                        else tmp.put(word, 1);

                        //단어별 Regularization 을 위한 단어별 count
                        if(allWordCount.containsKey(word)) allWordCount.put(word,allWordCount.get(word)+1);
                        else allWordCount.put(word,1);
                        }
                    }}
                }}
            }
        }
        //primary keyword 가중치 모듈
        add_primary_keyword(125,depth3_categoryVO);
        //Thread 사용한 병렬처리 code, HashMap 의 key 중복 이슈 때문에 안씀.
        //ConcurrentHashMap 쓰면 thread lock 걸린다고 함. 나중에 써보기!
        /*
        ForkJoinPool myPool = new ForkJoinPool(7);
        Stream<RecruitVO> parallelStream = SingleDepart.parallelStream();

        myPool.submit(()-> {
            parallelStream.forEach(vo->{
                if(vo.getCat_key().length()>0) {
                    String[] cat = vo.getCat_key().split("\\|");
                    if (vo.getRec_division().length() > 0) {
                        List<String> related_words = KomoranModule.Tokenizer(vo.getRec_division(),komoran);
                        System.out.println(vo.getKeyword());
                        String[] keyword = vo.getKeyword().split("·|,",100);
                        for(String c:keyword){
                            System.out.println(c);
                        }
                        for (String c : cat) {
                            CategoryVO tmpVO = depth3_categoryVO.get(c);
                            HashMap<String, Integer> tmp = tmpVO.getRelated_words();
                            for (String ls : related_words) {
                                if (tmp.containsKey(ls)) tmp.put(ls, tmp.get(ls) + 1);
                                else tmp.put(ls, 1);
                            }
                            tmp = tmpVO.getKeyword();
                            for(String ls:keyword){
                                if(tmp.containsKey(ls)) tmp.put(ls,tmp.get(ls)+1);
                                else tmp.put(ls,1);
                            }
                        }
                    }
                }
            });
        });
        */
    }


    public void set_12depth_category_vo(){
        List<CategoryVO> list_3depth = new ArrayList<>(depth3_categoryVO.values());
        for(CategoryVO vo:list_3depth){
            String category_num = vo.getCat_key();
            HashMap<String,Integer> depth3_related_words = vo.getRelated_words();
            CategoryVO cat_2depth = depth2_categoryVO.get(category_num.substring(0,category_num.length()-2));
            cat_2depth.setRelated_words_count(cat_2depth.getRelated_words_count() + vo.getRelated_words_count());
            HashMap<String,Integer> depth2_related_words = cat_2depth.getRelated_words();
            CategoryVO cat_1depth = depth1_categoryVO.get(category_num.substring(0,category_num.length()-4));
            HashMap<String,Integer> depth1_related_words = cat_1depth.getRelated_words();
            /*값이 있으면 더하고, 없으면 put*/
            for(HashMap.Entry<String,Integer> map:depth3_related_words.entrySet()){
                if(depth2_related_words.containsKey(map.getKey()))
                    depth2_related_words.put(map.getKey(),depth2_related_words.get(map.getKey())+map.getValue());
                else depth2_related_words.put(map.getKey(),map.getValue());
                if(depth1_related_words.containsKey(map.getKey()))
                    depth1_related_words.put(map.getKey(),depth1_related_words.get(map.getKey())+map.getValue());
                else depth1_related_words.put(map.getKey(),map.getValue());
            }
        }
        add_primary_keyword(625,depth2_categoryVO);
    }

    //factor = primary keyword 가중치
    private void add_primary_keyword(int factor, HashMap<String, CategoryVO> categoryVO){
        for(CategoryVO vo:categoryVO.values()){
            for(String primary_keyword:vo.getPrimary_keywords().split(",")){
                if(vo.getRelated_words().containsKey(primary_keyword))
                    vo.getRelated_words().put(primary_keyword,vo.getRelated_words().get(primary_keyword)+factor);
                else vo.getRelated_words().put(primary_keyword,factor);
            }
        }
    }

    public List<CategoryVO> category_print(){
        List<CategoryVO> list3 = new ArrayList<>(depth3_categoryVO.values());
        List<CategoryVO> list2 = Lists.newArrayList(depth2_categoryVO.values());
        List<CategoryVO> list1 = Lists.newArrayList(depth1_categoryVO.values());
        list3.sort(CategoryVO::compareTo);
        list2.sort(CategoryVO::compareTo);
        list1.sort(CategoryVO::compareTo);

        CategoryDAO.vo_to_category(list3,"Mapping_3depth");
        CategoryDAO.vo_to_category(list2,"Mapping_2depth");
        CategoryDAO.vo_to_category(list1,"Mapping_1depth");
        return list3;
    }

    public Map<String,Object> analysis_2depth_mapping(String rec_idx){
        if(TestSet.containsKey(rec_idx)){
            Map<String,Object> result = new HashMap<>();
            RecruitVO recruit = TestSet.get(rec_idx);
            HashMap<String,Object> analysis_count = analysis_count(recruit,depth2_categoryVO,0);
            result.put("rec_idx",recruit.getRec_idx());
            result.put("company_name",recruit.getCompany_nm());
            result.put("title",recruit.getTitle());
            result.put("category",recruit.getCat_key());
            result.put("rec_division",recruit.getRec_division());
            result.put("analysis_count",analysis_count);
            return result;
        }else{
            return null;
        }
    }

    public Map<String,Object> analysis_3depth_mapping(String rec_idx){
        if(TestSet.containsKey(rec_idx)){
            Map<String,Object> result = new HashMap<>();
            RecruitVO recruit = TestSet.get(rec_idx);
            HashMap<String,Object> analysis_count = analysis_count(recruit,depth3_categoryVO,1);
            result.put("rec_idx",recruit.getRec_idx());
            result.put("company_name",recruit.getCompany_nm());
            result.put("title",recruit.getTitle());
            result.put("category",recruit.getCat_key());
            result.put("rec_division",recruit.getRec_division());
            result.put("analysis_count",analysis_count);
            return result;
        }else{
            return null;
        }
    }

    /***return 값 : rec_map<String,cat_list<point_map<category,depth,point,etc...>>>***/
    private HashMap<String,Object> analysis_count(RecruitVO vo, HashMap<String, CategoryVO> categoryVO,int idx){
        HashMap<String,Object> recruit_map = new HashMap<>();
        String[] Rec_division = vo.getRec_division().split(",",100);
        String[] cat_key = vo.getCat_key().split("\\|",100);
        for(String rec:Rec_division){
            rec = rec.toLowerCase();
            List<String> tokens = komoran.Tokenizer(rec);
            HashMap<String,CategoryVO> cat_vo = new HashMap<>();

            if(idx == 0) for(String key:categoryVO.keySet()) cat_vo.put(key,categoryVO.get(key));
            else for(String key:cat_key) cat_vo.put(key,categoryVO.get(key));

            if(tokens.size()>0) {
                Map<String,Map<String,Object>> cat_map = make_cat(tokens,cat_vo);
                //값별로 정렬하기 위해 만든 리스트
                List<Map<String,Object>> cat_list = new ArrayList<>(cat_map.values());
                cat_list.sort((t1, t2) -> Double.compare((Double) t2.get("avg_percent"), (Double) t1.get("avg_percent")));
                recruit_map.put(rec,cat_list);
            }
        }
        return recruit_map;
    }


    public HashMap<String,Object> analysis_one(String rec){
        rec = rec.toLowerCase();
        List<String> tokens = komoran.Tokenizer(rec);
        HashMap<String,Object> recruit_map = new HashMap<>();
        Map<String,Map<String,Object>> cat_map = make_cat(tokens,depth2_categoryVO);
        //값별로 정렬하기 위해 만든 리스트
        List<Map<String,Object>> cat_list = new ArrayList<>(cat_map.values());
        cat_list.sort((t1, t2) -> Double.compare((Double) t2.get("avg_percent"), (Double) t1.get("avg_percent")));
        recruit_map.put(rec,cat_list);
        return recruit_map;
    }

    private TreeMap<String,Map<String,Object>> make_cat(List<String> tokens,HashMap<String, CategoryVO> categoryVO){
        TreeMap<String,Map<String,Object>> cat_map = new TreeMap<>();
        for(String key : categoryVO.keySet()) { cat_map.put(key, make_point_map(tokens, key, categoryVO)); }
        point_to_percent(cat_map);
        return cat_map;
    }

    private Map<String,Object> make_point_map(List<String> tokens, String cat, HashMap<String, CategoryVO> categoryVO){
        Map<String,Object> point_map = new HashMap<>();
        int total_count = 0;
        Map<String,Integer> count_list_separate_words = new HashMap<>();
        for (String token : tokens) {
            HashMap<String, Integer> related_words = categoryVO.get(cat).getRelated_words();
            if (related_words.containsKey(token)) {
                total_count += related_words.get(token);
                if (count_list_separate_words.containsKey(token))
                    count_list_separate_words.put(token, count_list_separate_words.get(token) + related_words.get(token));
                else count_list_separate_words.put(token, related_words.get(token));
            }
        }
        //단어별 영향력 정제
        Map<String,Double> reg_words_count = new HashMap<>();
        for(String word : count_list_separate_words.keySet()){
            int cat3_diff_count = 0, cat2_diff_count = 0, cat1_diff_count = 0;
            int count = count_list_separate_words.get(word);
            for(String str:categoryVO.keySet()) {
                if(categoryVO.get(str).getRelated_words().containsKey(word)) {
                    if(Integer.parseInt(str) % 10000 == Integer.parseInt(cat) % 10000) cat2_diff_count += categoryVO.get(str).getRelated_words().get(word);
                    else cat2_diff_count += categoryVO.get(str).getRelated_words().get(word);
                }
            }
            Double token_regularization_score = RegulizationModule.corpus_regularization(count,cat1_diff_count,cat2_diff_count);
            reg_words_count.put(word, token_regularization_score);
        }
        //직종별 영향력 정제
        double reg_category_count = RegulizationModule.category_regularization(total_count,categoryVO.get(cat).getRelated_words_count(),3000);

        point_map.put("category_num",categoryVO.get(cat).getCat_name()+" ("+cat+")");
        point_map.put("total_count",total_count);
        point_map.put("words_count",count_list_separate_words);
        point_map.put("reg_category_count",reg_category_count);
        point_map.put("reg_words_count",reg_words_count);

        return point_map;
    }

    private void point_to_percent(TreeMap<String,Map<String,Object>> cat_map){
        ArrayList<Double> count_list = new ArrayList<>();
        ArrayList<Double> reg_category_list = new ArrayList<>();
        ArrayList<Double> reg_words_list = new ArrayList<>();
        ArrayList<HashMap<String,Double>> reg_word_list = new ArrayList<>();

        for(Map<String,Object> point_map : cat_map.values()){
            count_list.add(Double.valueOf((Integer)point_map.get("total_count")));
            reg_category_list.add((Double)point_map.get("reg_category_count"));
            HashMap<String,Double> reg_word = (HashMap<String, Double>) point_map.get("reg_words_count");
            reg_word_list.add(reg_word);
            reg_words_list.add(reg_word.values().stream().mapToDouble(Double::doubleValue).sum());
        }

        ArrayList<Double> count_list_percent = RegulizationModule.count_to_percent(count_list);
        ArrayList<Double> reg_cat_list_per = RegulizationModule.count_to_percent(reg_category_list);
        ArrayList<HashMap<String,String>> reg_token_per = RegulizationModule.count_to_percent2(reg_word_list);
        ArrayList<Double> reg_tokens_per = RegulizationModule.count_to_percent(reg_words_list);
        int x=0;
        for(String key:cat_map.keySet()){
            Map<String,Object> temp = cat_map.get(key);
            temp.put("count_percent",String.format("%.2f",count_list_percent.get(x))+"%");
            temp.put("reg_cat_percent",String.format("%.2f",reg_cat_list_per.get(x))+"%");
            temp.put("reg_tokens_percent",String.format("%.2f",reg_tokens_per.get(x))+"%");
            temp.put("reg_token_percent",reg_token_per.get(x));
            temp.put("avg_percent", (reg_tokens_per.get(x)+reg_cat_list_per.get(x))/2);
            temp.put("avg_per", String.format("%.2f",(reg_tokens_per.get(x)+reg_cat_list_per.get(x))/2)+"%");
            x++;
        }
    }

/*
    private HashMap<String,Object> analysis_3depth_count(RecruitVO vo){
        HashMap<String,Object> recruit_map = new HashMap<>();
        String[] Rec_division = vo.getRec_division().split(",",100);
        String[] cat_key = vo.getCat_key().split("\\|",100);
        for(String rec:Rec_division){
            rec = rec.toLowerCase();
            List<String> tokens = komoran.Tokenizer(rec);
            if(tokens.size()>0) {
                Map<Integer,Map<String,Object>> cat_map = make_cat2(cat_key,tokens);
                List<Map<String,Object>> cat_list = new ArrayList<>(cat_map.values());

                cat_list.sort((t1, t2) -> Double.compare((Double) t2.get("avg_percent"), (Double) t1.get("avg_percent")));
                recruit_map.put(rec,cat_list);
            }
        }
        return recruit_map;
    }

    private TreeMap<Integer,Map<String,Object>> make_cat2(String[] cat_key, List<String> tokens){
        TreeMap<Integer,Map<String,Object>> cat_map = new TreeMap<>();
        ArrayList<Double> count_list = new ArrayList<>();
        ArrayList<Double> regularization_list = new ArrayList<>();

        for (String cat : cat_key) {
            Map<String,Object> point_map = new HashMap<>();
            int rec_point = 0;
            Map<String,Integer> count_list_separate_words = new HashMap<>();
            for (String token : tokens) {
                CategoryVO tmp = depth3_categoryVO.get(cat);
                if (tmp != null) {
                    HashMap<String, Integer> related_words = tmp.getRelated_words();
                    if (related_words.containsKey(token)) {
                        rec_point += related_words.get(token);
                        if (count_list_separate_words.containsKey(token))
                            count_list_separate_words.put(token, count_list_separate_words.get(token) + related_words.get(token));
                        else count_list_separate_words.put(token, related_words.get(token));
                    }
                }
            }
            double regularization_count = RegulizationModule.category_regularization(rec_point,depth3_categoryVO.get(cat).getRelated_words_count(),1000);

            count_list.add((double)rec_point);
            regularization_list.add(regularization_count);
            point_map.put("category_num",depth3_categoryVO.get(cat).getCat_name()+" ("+cat+")");
            point_map.put("total_count",rec_point);
            point_map.put("regularization_count",regularization_count);
            point_map.put("tokens_count",count_list_separate_words);
            cat_map.put(Integer.parseInt(cat),point_map);
        }
        ArrayList<Double> reg_token_sum = new ArrayList<>();
        ArrayList<HashMap<String,Double>> reg_token_counts = new ArrayList<>();


        for(Integer key:cat_map.keySet()){
            Map<String,Object> temp = cat_map.get(key);
            HashMap<String, Double> reg_token_count = new HashMap<>();
            HashMap<String,Double> token = (HashMap<String, Double>) temp.get("tokens_count");
            for(String tok:token.keySet()) {
                HashMap<Integer,Integer> category_token_nm = new HashMap<>();
                for(String str:depth3_categoryVO.keySet()){
                    Integer k = Integer.parseInt(str);
                    category_token_nm.put(k, depth3_categoryVO.get(k.toString()).getRelated_words().getOrDefault(tok, 0));
                }
                for (Integer k : cat_map.keySet()) {
                    HashMap<String, Integer> tmp = (HashMap<String, Integer>) temp.get("tokens_count");
                    Double token_regularization_score = RegulizationModule.corpus_regularization2(k, tmp.get(tok), category_token_nm);
                    reg_token_count.put(tok, token_regularization_score);
                }
            }
            temp.put("reg_token_count",reg_token_count);
            reg_token_counts.add(reg_token_count);
            reg_token_sum.add(reg_token_count.values().stream().mapToDouble(Double::doubleValue).sum());
        }


        //percent
        ArrayList<Double> count_list_percent= RegulizationModule.count_to_percent(count_list);
        ArrayList<Double> regularization_list_percent= RegulizationModule.count_to_percent(regularization_list);
        ArrayList<Double> reg_tokens_per = RegulizationModule.count_to_percent(reg_token_sum);
        ArrayList<HashMap<String,String>> reg_token_per = RegulizationModule.count_to_percent2(reg_token_counts);
        int x=0;
        for(Integer key:cat_map.keySet()){
            Map<String,Object> temp = cat_map.get(key);
            HashMap<String,Double> tk = (HashMap<String, Double>) temp.get("tokens_regularization_count");
            temp.put("count_percent",String.format("%.2f",count_list_percent.get(x))+"%");
            temp.put("regularization_percent",String.format("%.2f",regularization_list_percent.get(x))+"%");
            temp.put("reg_tokens_percent",String.format("%.2f",reg_tokens_per.get(x))+"%");
            temp.put("reg_token_percent",reg_token_per.get(x));
            temp.put("avg_percent", (reg_tokens_per.get(x)+regularization_list_percent.get(x))/2);
            temp.put("avg_per", String.format("%.2f",(reg_tokens_per.get(x)+regularization_list_percent.get(x))/2)+"%");
            x++;
        }
        return cat_map;
    }


 */
}