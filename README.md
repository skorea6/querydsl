# QueryDsl
#### 인프런 강의: [실전! Querydsl](https://www.inflearn.com/course/querydsl-%EC%8B%A4%EC%A0%84/dashboard)


## 초기 설정
* Gradle IntelliJ 사용법
  - Gradle -> Tasks -> build -> clean
  - Gradle -> Tasks -> other -> compileQuerydsl

* Gradle 콘솔 사용법
  - ./gradlew clean compileQuerydsl

* Q 타입 생성 확인
  - build -> generated -> querydsl
  - study.querydsl.entity.QHello.java 파일이 생성되어 있어야 함

* 참고: Q타입은 컴파일 시점에 자동 생성되므로 버전관리(GIT)에 포함하지 않는 것이 좋다.
  - 앞서 설정에서 생성 위치를 gradle build 폴더 아래 생성되도록 했기 때문에 이 부분도 자연스럽게 해결된다.
  - (대부분 gradle build 폴더를 git에 포함하지 않는다.)

* JPQL vs Querydsl
  - JPQL: 문자(실행 시점 오류), Querydsl: 코드(컴파일 시점 오류)
  - JPQL: 파라미터 바인딩 직접, Querydsl: 파라미터 바인딩 자동 처리


## 기본 Q-Type 활용
- 'Q클래스 인스턴스'를 사용하는 2가지 방법
  ```java
  QMember qMember = new QMember("m"); // 별칭 직접 지정
  QMember qMember = QMember.member; // 기본 인스턴스 사용
  ```

- '기본 인스턴스'를 static import와 함께 사용 (권장!)
  - import static study.querydsl.entity.QMember.*;
  - 이후, member 로.

- 같은 테이블을 조인해야 하는 경우가 아니면 기본 인스턴스를 사용하자


## 검색 조건 쿼리
- select 와 from 을 합쳐서 .selectFrom(member) 식으로 표현 가능
  ```java
  member.username.eq("member1") // username = 'member1'
  member.username.ne("member1") //username != 'member1'
  
  member.username.eq("member1").not() // username != 'member1'
  member.username.isNotNull() //이름이 is not null
  member.age.in(10, 20) // age in (10,20)
  member.age.notIn(10, 20) // age not in (10, 20)
  member.age.between(10,30) //between 10, 30
  member.age.goe(30) // age >= 30
  member.age.gt(30) // age > 30
  member.age.loe(30) // age <= 30
  member.age.lt(30) // age < 30
  
  member.username.like("member%") //like 검색
  member.username.contains("member") // like ‘%member%’ 검색
  member.username.startsWith("member") //like ‘member%’ 검색
  ```

- and 를 생략하고 ',' 로 and 대신 가능
  ```java
  member.username.eq("member1").and(member.age.eq(10))
  member.username.eq("member1"), member.age.eq(10)
  ```



## 결과 조회
- fetch() : 리스트 조회, 데이터 없으면 빈 리스트 반환
- fetchOne() : 단 건 조회
	- 결과가 없으면 : null
	- 결과가 둘 이상이면 : NonUniqueResultException 터짐
- fetchFirst() : limit(1).fetchOne()
- fetchResults() : 페이징 정보 포함, total count 쿼리 추가 실행
- fetchCount() : count 쿼리로 변경해서 count 수 조회


## 정렬
- desc() , asc() : 일반 정렬
- nullsLast() , nullsFirst() : null 데이터 순서 부여


## 페이징
- 실무에서 페이징 쿼리를 작성할 때, 데이터를 조회하는 쿼리는 여러 테이블을 조인해야 하지만, count 쿼리는 조인이 필요 없는 경우도 있다. 
- 그런데 이렇게 자동화된 count 쿼리는 원본 쿼리와 같이 모두 조인을 해버리기 때문에 성능이 안나올 수 있다.
- count 쿼리에 조인이 필요없는 성능 최적화가 필요하다면, count 전용 쿼리를 별도로 작성해야 한다.


## 조인
- join(조인 대상, 별칭으로 사용할 Q타입) -> (예) .join(member.team, team)
- join() , innerJoin() : 내부 조인 (inner join)
- leftJoin() : left 외부 조인 (left outer join)
- rightJoin() : rigth 외부 조인 (rigth outer join)

- 세타조인:
	- 연관관계가 없는 필드로 조인
	- from 절에 여러 엔티티를 선택해서 세타 조인 .from(member, team)
	- 외부 조인 불가능

- 조인 on절:
	- 조인 대상 필터링:
		- 예) 회원과 팀을 조인하면서, 팀 이름이 teamA인 팀만 조인, 회원은 모두 조회
		- .leftJoin(member.team, team).on(team.name.eq("teamA"))
		-  외부조인이 아니라 내부조인(inner join)을 사용하면, where 절에서 필터링 하는 것과 기능이 동일하다. 따라서 on 절을 활용한 조인 대상 필터링을 사용할 때, 내부조인 이면 익숙한 where 절로 해결하고, 정말 외부조인이 필요한 경우에만 이 기능을 사용하자.

	- 연관관계가 없는 엔티티 외부 조인:
		- 예) 회원의 이름과 팀의 이름이 같은 대상 외부 조인
		- .leftJoin(team).on(member.username.eq(team.name))

- 조인 페치 조인:
	- 페치 조인은 SQL에서 제공하는 기능은 아니다.
	- SQL조인을 활용해서 연관된 엔티티를 SQL 한번에 조회하는 기능이다.
	- 주로 성능 최적화에 사용하는 방법
	- 예) 즉시로딩으로 Member, Team SQL 쿼리 조인으로 한번에 조회
	- .join(member.team, team).fetchJoin()


## 서브쿼리
```java
QMember memberSub = new QMember("memberSub");
JPAExpressions.select(memberSub.age).from(memberSub).where(memberSub.age.gt(10))
```
- select절, where절 들어갈 수 있음.
- 다만, JPA JPQL 서브쿼리의 한계점으로 from 절의 서브쿼리(인라인 뷰)는 지원하지 않는다. 당연히 Querydsl 도 지원하지 않는다.

- from 절의 서브쿼리 해결방안:
  1. 서브쿼리를 join으로 변경한다. (가능한 상황도 있고, 불가능한 상황도 있다.)
  2. 애플리케이션에서 쿼리를 2번 분리해서 실행한다.
  3. nativeSQL을 사용한다.

## case문
- 단순할때는: member.age.when().then().otherwhise()
- 복잡할때는: new CaseBuilder().when().then().otherwhise()

## 상수,문자 더하기
- 상수: Expressions.constant("A")
- 문자더하기 (concat): member.username.concat("_").concat(member.age.stringValue())
- 문자가 아닌 다른 타입들은 stringValue() 로 문자로 변환할 수 있다


## 프로젝션과 결과 반환
- 프로젝션 대상은 select() 에 들어가는거
  - 프로젝션 대상이 하나: List<String>
  - 프로젝션 대상이 둘 이상: List<Tuple> 혹은 DTO

- DTO-1 프로퍼티 접근: Projections.bean(MemberDto.class, member.username, member.age)
- DTO-2 필드 직접 접근: Projections.fields(MemberDto.class, member.username, member.age)
- DTO-3 생성자 접근: Projections.constructor(MemberDto.class, member.username, member.age)

- 별칭이 다를때는 .as("name") 로 이름을 맞춰준다.
- ExpressionUtils.as(source, alias) : 필드나, 서브 쿼리에 별칭 적용

- @QueryProjection : 컴파일러로 타입을 체크할 수 있으므로 가장 안전한 방법이나, DTO에 QueryDSL 어노테 이션을 유지해야 하는 점과 DTO까지 Q파일을 생성해야 하는 단점이 있다.


## 동적 쿼리
- BooleanBuilder: BooleanBuilder booleanBuilder = new BooleanBuilder();
- Where 다중 파라미터 사용: .where(usernameEq(usernameCond), ageEq(ageCond))
  - where 조건에 null 값은 무시된다.
  - 메서드를 다른 쿼리에서도 재활용 할 수 있다.
  - 쿼리 자체의 가독성이 높아진다. (실무추천)


## 벌크 연산
- 수정: .update(member).set(member.username, "비회원")
- 벌크연산은 영속성 콘텍스트를 무시하고 바로 DB의 값을 바꿔 버린다.
- 벌크연산 후, selectFrom 해서 가져올때, DB의 값을 가져와도 영속성 콘텍스트가 우선순위를 가지기 때문에 바뀌기 전의 값이 나온다.
- 그러므로, em.flush(); em.clear(); 를 통해 영속성 콘텍스트를 꼭 초기화 하는 것이 안전하다.

- 삭제: .delete(member).where(member.age.gt(18))


## SQL function 호출하기
- SQL function은 JPA와 같이 Dialect에 등록된 내용만 호출할 수 있다.
- 예) member M으로 변경하는 replace 함수 사용
  ```java
  String result = queryFactory.select(Expressions.stringTemplate("function('replace', {0},{1},{2})", member.username, "member", "M"))
  ```


## fetchResults(), fetchCount() : Deprecated(향후 미지원)
- count 쿼리가 필요하면 별도로 작성해야한다.
- count(*) 을 사용하고 싶으면 Wildcard.count를 사용 .select(Wildcard.count)
- 응답 결과는 숫자 하나이므로 fetchOne() 을 사용
- fetchResults 대신, count 쿼리와 실제 쿼리 2번을 직접 작성한다. (searchPageComplex 참조)


## PageableExecutionUtils 클래스 사용 패키지 변경
- 기능이 Deprecated 된 것은 아니고, 사용 패키지 위치가 변경됨
- 기존 위치를 신규 위치로 변경해 주시면 문제 없이 사용할 수 있음
- 기존: org.springframework.data.repository.support.PageableExecutionUtils
- 신규: org.springframework.data.support.PageableExecutionUtils
