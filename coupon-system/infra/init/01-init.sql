CREATE SCHEMA IF NOT EXISTS coupon;

USE coupon;

CREATE TABLE if NOT EXISTS coupon_event (
      id bigint auto_increment primary key,
      name varchar(100) not null,
      total_quantity int not null,
      issued_quantity int not null,
      start_at datetime(6) not null,
      end_at datetime(6) not null,
      created_at datetime(6) not null,
      updated_at datetime(6) not null
)
ENGINE=InnoDB
COMMENT='쿠폰 이벤트';

CREATE TABLE if NOT EXISTS coupon_issue (
      id bigint auto_increment primary key,
      coupon_event_id bigint not null,
      user_id bigint not null,
      issued_at datetime(6) not null,
      constraint uk_coupon_issue_event_user unique (coupon_event_id, user_id),
      constraint fk_coupon_issue_event foreign key (coupon_event_id) references coupon_event(id)
)
ENGINE=InnoDB
COMMENT='쿠폰 발급';

CREATE INDEX idx_coupon_issue_user_id on coupon_issue(user_id);
CREATE INDEX idx_coupon_issue_event_id on coupon_issue(coupon_event_id);
