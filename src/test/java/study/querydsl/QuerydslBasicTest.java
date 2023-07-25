package study.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceUnit;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.team;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {

    @PersistenceContext
    EntityManager em;

    JPAQueryFactory queryFactory;

    @BeforeEach
    public void before() {
        queryFactory = new JPAQueryFactory(em);

        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");

        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);
        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);

        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);

    }

    // [순수 JPQL]
    @Test
    public void startJPQL() {
        //member1을 찾아라.
        String qlString = "select m from Member m where m.username = :username";

        Member findMember = em.createQuery(qlString, Member.class)
                .setParameter("username", "member1")
                .getSingleResult();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    // [QueryDsl]
    @Test
    public void startQuerydsl() {
        //member1을 찾아라.
        //QMember m = new QMember("m");
        Member findMember = queryFactory
                .select(member)
                .from(member)
                .where(member.username.eq("member1"))    //파라미터 바인딩 처리
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }


    @Test
    public void search() throws Exception {
        //given
        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1").and(member.age.eq(10)))
                .fetchOne();

        //when

        //then
        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void searchAndParam() {
        // and 를 생략하고 ',' 로 and 대신 가능
        List<Member> result1 = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1"), member.age.eq(10))
                .fetch();
        assertThat(result1.size()).isEqualTo(1);
    }

    @Test
    void resultFetch() throws Exception {
        // 페이지와 쿼리 함께 (총 2번 나감 = 카운트 쿼리 + 기본 쿼리)
        QueryResults<Member> results = queryFactory
                .selectFrom(member)
                .fetchResults();

        results.getTotal(); // 총 개수 가져오기
        List<Member> content = results.getResults();
    }

    /**
     * 회원 정렬 순서
     * 1. 회원 나이 내림차순(desc)
     * 2. 회원 이름 올림차순(asc)
     * 단 2에서 회원 이름이 없으면 마지막에 출력 (nulls last)
     */
    @Test
    public void sort() {
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));
        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast())
                .fetch();
        Member member5 = result.get(0);
        Member member6 = result.get(1);
        Member memberNull = result.get(2);
        assertThat(member5.getUsername()).isEqualTo("member5");
        assertThat(member6.getUsername()).isEqualTo("member6");
        assertThat(memberNull.getUsername()).isNull();
    }

    @Test
    void paging1() throws Exception {
        //given
        List<Member> result = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1) //0부터 시작(zero index)
                .limit(2) //최대 2건 조회
                .fetch();

        assertThat(result.size()).isEqualTo(2);
    }

    @Test
    void paging2() throws Exception {
        //given
        QueryResults<Member> queryResults = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1) //0부터 시작(zero index)
                .limit(2) //최대 2건 조회
                .fetchResults(); // paging

        assertThat(queryResults.getTotal()).isEqualTo(4);
        assertThat(queryResults.getLimit()).isEqualTo(2);
        assertThat(queryResults.getOffset()).isEqualTo(1);
        assertThat(queryResults.getResults().size()).isEqualTo(2);
    }

    /**
     * JPQL
     * select
     * COUNT(m), //회원수
     * SUM(m.age), //나이 합
     * AVG(m.age), //평균 나이
     * MAX(m.age), //최대 나이
     * MIN(m.age) //최소 나이
     * from Member m
     */
    @Test
    public void aggregation() throws Exception {
        List<Tuple> result = queryFactory
                .select(member.count(),
                        member.age.sum(),
                        member.age.avg(),
                        member.age.max(),
                        member.age.min())
                .from(member)
                .fetch();

        Tuple tuple = result.get(0); // 튜플 보다는 dto 추천
        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(100);
        assertThat(tuple.get(member.age.avg())).isEqualTo(25);
        assertThat(tuple.get(member.age.max())).isEqualTo(40);
        assertThat(tuple.get(member.age.min())).isEqualTo(10);
    }

    /**
     * 팀의 이름과 각 팀의 평균 연령을 구해라.
     */
    @Test
    public void group() throws Exception {
        List<Tuple> result = queryFactory
                .select(team.name, member.age.avg())
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
                .fetch();
        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);
        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamA.get(member.age.avg())).isEqualTo(15);
        assertThat(teamB.get(team.name)).isEqualTo("teamB");
        assertThat(teamB.get(member.age.avg())).isEqualTo(35);
    }

    /**
     * 팀A에 소속된 모든 회원
     */
    @Test
    public void join() throws Exception {
        List<Member> result = queryFactory
                .selectFrom(member)
                .join(member.team, team) // leftJoin, rightJoin 도 가능
                .where(team.name.eq("teamA"))
                .fetch();
        assertThat(result).extracting("username").containsExactly("member1", "member2");
    }

    /**
     * 세타 조인 (연관관계가 없는 필드로 조인)
     * 회원의 이름이 팀 이름과 같은 회원 조회
     * - from 절에 여러 엔티티를 선택해서 세타 조인
     * - 외부 조인 불가능
     */
    @Test
    public void theta_join() throws Exception {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        List<Member> result = queryFactory
                .select(member)
                .from(member, team)
                .where(member.username.eq(team.name))
                .fetch();
        assertThat(result)
                .extracting("username")
                .containsExactly("teamA", "teamB");
    }


    /**
     * 1. 조인 대상 필터링
     * 예) 회원과 팀을 조인하면서, 팀 이름이 teamA인 팀만 조인, 회원은 모두 조회
     * JPQL: SELECT m, t FROM Member m LEFT JOIN m.team t on t.name = 'teamA'
     * SQL: SELECT m.*, t.* FROM Member m LEFT JOIN Team t ON m.TEAM_ID=t.id and t.name='teamA'
     * - on 절을 활용한 조인 대상 필터링을 사용할 때, 내부조인(inner join, 그냥 join) 이면 익숙한 where 절로 해결하고,
     * - 정말 외부조인이 필요한 경우에만 이 기능을 사용하자.
     */
    @Test
    public void join_on_filtering() throws Exception {
        List<Tuple> result = queryFactory
                .select(member, team) // select 가 여러가지 타입이라 tuple로 결과나옴
                .from(member)
                .leftJoin(member.team, team).on(team.name.eq("teamA"))
                .fetch();
        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * 2. 연관관계 없는 엔티티 외부 조인
     * 예) 회원의 이름과 팀의 이름이 같은 대상 외부 조인
     * JPQL: SELECT m, t FROM Member m LEFT JOIN Team t on m.username = t.name
     * SQL: SELECT m.*, t.* FROM Member m LEFT JOIN Team t ON m.username = t.name
     *
     * - 주의! 문법을 잘 봐야 한다. leftJoin() 부분에 일반 조인과 다르게 엔티티 하나만 들어간다.
     *  - 일반조인: leftJoin(member.team, team)
     *  - on조인: from(member).leftJoin(team).on(xxx)
     */
    @Test
    public void join_on_no_relation() throws Exception {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));

        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(team).on(member.username.eq(team.name)) // 연관관계가 없으므로 그냥 'team' 하나만.
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("t=" + tuple);
        }
    }

    @PersistenceUnit
    EntityManagerFactory emf;
    @Test
    public void fetchJoinNo() throws Exception {
        em.flush();
        em.clear();
        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne();
        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치 조인 미적용").isFalse();
    }

    @Test
    public void fetchJoinUse() throws Exception {
        em.flush();
        em.clear();
        Member findMember = queryFactory
                .selectFrom(member)
                .join(member.team, team).fetchJoin() // fetchJoin
                .where(member.username.eq("member1"))
                .fetchOne();
        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치 조인 적용").isTrue();
    }

    /**
     * 서브쿼리1
     * 나이가 가장 많은 회원 조회
     */
    @Test
    public void subQuery() throws Exception {
        QMember memberSub = new QMember("memberSub");
        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(
                        JPAExpressions.select(memberSub.age.max()).from(memberSub)
                ))
                .fetch();
        assertThat(result).extracting("age").containsExactly(40);
    }

    /**
     * 서브쿼리2
     * 나이가 평균 나이 이상인 회원
     */
    @Test
    public void subQueryGoe() throws Exception {
        QMember memberSub = new QMember("memberSub");
        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.goe( // greater or equal (이상) >=
                        JPAExpressions.select(memberSub.age.avg()).from(memberSub)
                ))
                .fetch();
        assertThat(result).extracting("age").containsExactly(30, 40);
    }

    /**
     * 서브쿼리3
     * 서브쿼리 여러 건 처리, in 사용
     */
    @Test
    public void subQueryIn() throws Exception {
        QMember memberSub = new QMember("memberSub");
        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.in(
                        JPAExpressions
                                .select(memberSub.age)
                                .from(memberSub)
                                .where(memberSub.age.gt(10))
                )) .fetch();
        assertThat(result).extracting("age").containsExactly(20, 30, 40);
    }

    /**
     * [from 절의 서브쿼리 한계]
     * - JPA JPQL 서브쿼리의 한계점으로 from 절의 서브쿼리(인라인 뷰)는 지원하지 않는다.
     * - 당연히 Querydsl 도 지원하지 않는다.
     * - 하이버네이트 구현체를 사용하면 select 절의 서브쿼리는 지원한다.
     * - Querydsl도 하이버네이트 구현체를 사용하면 select 절의 서브쿼리를 지원한다.
     *
     * [from 절의 서브쿼리 해결방안]
     * 1. 서브쿼리를 join으로 변경한다. (가능한 상황도 있고, 불가능한 상황도 있다.)
     * 2. 애플리케이션에서 쿼리를 2번 분리해서 실행한다.
     * 3. nativeSQL을 사용한다.
     */
    @Test
    void selectSubQuery() throws Exception {
        //given
        QMember memberSub = new QMember("memberSub");
        List<Tuple> fetch = queryFactory
                .select(member.username,
                        JPAExpressions // JPAExpressions static import 해도 됨. 그냥 select 로 쓰게끔.
                                .select(memberSub.age.avg())
                                .from(memberSub))
                .from(member)
                .fetch();

        for(Tuple tuple : fetch) {
            System.out.println("username = " + tuple.get(member.username));
            System.out.println("age = " + tuple.get(JPAExpressions.select(memberSub.age.avg()).from(memberSub)));
        }
    }

    @Test
    void basicCase() throws Exception {
        List<String> result = queryFactory
                .select(member.age
                        .when(10).then("열살")
                        .when(20).then("스무살")
                        .otherwise("기타")
                )
                .from(member)
                .fetch();

        for(String s : result){
            System.out.println("s = " + s);
        }
    }

    @Test
    void complexCase() throws Exception {
        List<String> result = queryFactory
                .select(new CaseBuilder()
                        .when(member.age.between(0, 20)).then("0~20살")
                        .when(member.age.between(21, 30)).then("21~30살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for(String s : result){
            System.out.println("s = " + s);
        }
    }

    /**
     * 예를 들어서 다음과 같은 임의의 순서로 회원을 출력하고 싶다면?
     * 1. 0 ~ 30살이 아닌 회원을 가장 먼저 출력
     * 2. 0 ~ 20살 회원 출력
     * 3. 21 ~ 30살 회원 출력
     *
     * - Querydsl은 자바 코드로 작성하기 때문에 rankPath 처럼 복잡한 조건을 변수로 선언해서 select 절, orderBy 절에서 함께 사용할 수 있다.
     */
    @Test
    void complexCase2() throws Exception {
        NumberExpression<Integer> rankPath = new CaseBuilder()
                .when(member.age.between(0, 20)).then(2)
                .when(member.age.between(21, 30)).then(3)
                .otherwise(1);

        List<Tuple> result = queryFactory
                .select(member.username, member.age, rankPath)
                .from(member)
                .orderBy(rankPath.asc())
                .fetch();

        for (Tuple tuple : result) {
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);
            Integer rank = tuple.get(rankPath);
            System.out.println("username = " + username + " age = " + age + " rank = " + rank);
        }
    }

    /**
     * 상수
     * 최적화가 가능하면 SQL에 constant 값을 넘기지 않는다.
     * 상수를 더하는 것(concat) 처럼 최적화가 어려우면 SQL에 constant 값을 넘긴다.
     */
    @Test
    void constant() throws Exception {
        Tuple result = queryFactory
                .select(member.username, Expressions.constant("A")) // 그냥 상수 A
                .from(member)
                .fetchFirst();

        System.out.println(result);
    }

    @Test
    void concat() throws Exception {
        // {username}_{age}
        List<String> result = queryFactory
                .select(member.username.concat("_").concat(member.age.stringValue())) // stringValue() 로 int->string concat. (ENUM 때도 사용)
                .from(member)
                .where(member.username.eq("member1"))
                .fetch();

        for(String s : result){
            System.out.println("s = " + s);
        }
    }

    // [DTO-1] 순수 JPQL 사용
    @Test
    void findDtoByJPQL() throws Exception {
        List<MemberDto> result = em.createQuery(
                        "select new study.querydsl.dto.MemberDto(m.username, m.age) from Member m", MemberDto.class)
                .getResultList();
        for(MemberDto memberDto : result){
            System.out.println("memberDto = " + memberDto);
        }
    }

    // [DTO-2] Querydsl Bean - 프로퍼티 접근 - Setter
    @Test
    void findDtoBySetter() throws Exception {
        List<MemberDto> result = queryFactory
                .select(Projections.bean(MemberDto.class,
                        member.username, member.age)) // sql 실행시 딱 이것만 select 해서 가져옴
                .from(member)
                .fetch();

        for(MemberDto memberDto : result){
            System.out.println("memberDto = " + memberDto);
        }
    }

    // [DTO-2] Querydsl Bean - 프로퍼티 접근 - Field
    @Test
    void findDtoByField() throws Exception {
        List<MemberDto> result = queryFactory
                .select(Projections.fields(MemberDto.class,
                        member.username, member.age)) // sql 실행시 딱 이것만 select 해서 가져옴
                .from(member)
                .fetch();

        for(MemberDto memberDto : result){
            System.out.println("memberDto = " + memberDto);
        }
    }

    // [DTO-2] Querydsl Bean - 프로퍼티 접근 - Constructor (생성자)
    @Test
    void findDtoByConstructor() throws Exception {
        List<MemberDto> result = queryFactory
                .select(Projections.constructor(MemberDto.class,
                        member.username, member.age)) // sql 실행시 딱 이것만 select 해서 가져옴
                .from(member)
                .fetch();

        for(MemberDto memberDto : result){
            System.out.println("memberDto = " + memberDto);
        }
    }

    // [DTO-2] Querydsl Bean - 별칭이 다를때 - 필드 (UserDto)
    @Test
    void findUserDto() throws Exception {
        QMember memberSub = new QMember("memberSub");
        List<UserDto> result = queryFactory
                .select(Projections.fields(UserDto.class,
                        member.username.as("name"),
                        ExpressionUtils.as(
                                JPAExpressions
                                .select(memberSub.age.max())
                                .from(memberSub), "age"
                        ))
                )
                .from(member)
                .fetch();

        for (UserDto userDto : result) {
            System.out.println("userDto = " + userDto);
        }
    }

    // [DTO-3] Querydsl Bean - 별칭이 다를때 - 생성자 (UserDto)
    @Test
    void findUserDtoByConstructor() throws Exception {
        List<UserDto> result = queryFactory
                .select(Projections.constructor(UserDto.class,
                        member.username, member.age
                )) // UserDto 에 생성자가 있어야함.
                .from(member)
                .fetch();

        for (UserDto userDto : result) {
            System.out.println("userDto = " + userDto);
        }
    }

    /**
     * 프로젝션과 결과 반환 - @QueryProjection
     * 위의 constructor 와 다르게 컴파일 오류 잡을 수 있다!
     * 이 방법은 컴파일러로 타입을 체크할 수 있으므로 가장 안전한 방법이다.
     * 다만 DTO에 QueryDSL 어노테이션을 유지해야 하는 점과 DTO까지 Q파일을 생성해야 하는 단점이 있다.
     */
    @Test
    void findDtoByQueryProjection() throws Exception {
        List<MemberDto> result = queryFactory
                .select(new QMemberDto(member.username, member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * 동적 쿼리 - BooleanBuilder
     * BooleanBuilder booleanBuilder = new BooleanBuilder();
     */
    @Test
    void dynamicQuery_BooleanBuilder() throws Exception {
        String usernameParam = "member1";
        Integer ageParam = null;
        List<Member> result = searchMember1(usernameParam, ageParam);
        Assertions.assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember1(String usernameCond, Integer ageCond) {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        if(usernameCond != null){
            booleanBuilder.and(member.username.eq(usernameCond));
        }

        if(ageCond != null){
            booleanBuilder.and(member.age.eq(ageCond));
        }

        return queryFactory
                .selectFrom(member)
                .where(booleanBuilder)
                .fetch();
    }

    /**
     * 동적 쿼리 - Where 다중 파라미터 사용
     * .where(usernameEq(usernameCond), ageEq(ageCond))
     * - where 조건에 null 값은 무시된다.
     * - 메서드를 다른 쿼리에서도 재활용 할 수 있다.
     * - 쿼리 자체의 가독성이 높아진다. (실무추천)
     */
    @Test
    void dynamicQuery_WhereParam() throws Exception {
        String usernameParam = "member1";
        Integer ageParam = null;
        List<Member> result = searchMember2(usernameParam, ageParam);
        Assertions.assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember2(String usernameCond, Integer ageCond) {
        return queryFactory
                .selectFrom(member)
//                .where(allEq(usernameCond, ageCond))
                .where(usernameEq(usernameCond), ageEq(ageCond))
                .fetch();
    }

    private BooleanExpression usernameEq(String usernameCond) {
        return usernameCond != null ? member.username.eq(usernameCond) : null;
    }

    private BooleanExpression ageEq(Integer ageCond) {
        return ageCond != null ? member.age.eq(ageCond) : null;
    }

    private BooleanExpression allEq(String usernameCond, Integer ageCond){
        return usernameEq(usernameCond).and(ageEq(ageCond));
    }

    /**
     * 수정, 삭제 벌크 연산
     * - 벌크연산은 영속성 콘텍스트를 무시하고 바로 DB에 값을 바꿔 버린다.
     * - 벌크연산후, selectFrom 해서 가져올때, DB의 값을 가져와도 영속성 콘텍스트가 우선순위를 가지기 때문에 바뀌기 전의 값이 나온다.
     */
    @Test
    void bulkUpdate() throws Exception {
        long count = queryFactory
                .update(member)
                .set(member.username, "비회원")
                .where(member.age.lt(28))
                .execute();

        // 영속성 콘텍스트 초기화 해주어야함.
        em.flush();
        em.clear();

        List<Member> result = queryFactory
                .selectFrom(member)
                .fetch();

        for (Member member1 : result) {
            System.out.println("member1 = " + member1);
        }
    }

    @Test
    void bulkAdd() throws Exception {
        long count = queryFactory
                .update(member)
                .set(member.age, member.age.multiply(2))
                .execute();
    }

    @Test
    void bulkDelete() throws Exception {
        long count = queryFactory
                .delete(member)
                .where(member.age.gt(18))
                .execute();
    }

    /**
     * SQL function 호출하기
     * - (1) member M으로 변경하는 replace 함수 사용
     * - (2) 소문자로 변경해서 비교해라.
     */
    @Test
    void sqlFunction() throws Exception {
        String result = queryFactory
                .select(
                        Expressions.stringTemplate("function('replace', {0}, {1}, {2})", member.username, "member", "M")
                )
                .from(member)
                .fetchFirst();

        System.out.println(result);
    }

    @Test
    void sqlFunction2() throws Exception {
        String result = queryFactory
                .select(member.username)
                .from(member)
                // .where(member.username.eq(member.username.lower())) => 이게 더 낫다.
                .where(member.username.eq(Expressions.stringTemplate("function('lower', {0})", member.username)))
                .fetchFirst();

        System.out.println(result);
    }
}