CREATE SCHEMA IF NOT EXISTS coupon;

USE coupon;

CREATE TABLE if NOT EXISTS coupon_event
(
    id bigint auto_increment primary key,
    name varchar(100) not null,
    total_quantity int not null,
    issued_quantity int not null,
    start_at datetime(6) not null,
    end_at datetime(6) not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    version bigint not null
)
    ENGINE=InnoDB
    COMMENT='쿠폰 이벤트';

CREATE TABLE if NOT EXISTS coupon_issue
(
    id bigint auto_increment primary key,
    coupon_event_id bigint not null,
    user_id bigint not null,
    issued_at datetime(6) not null,
    constraint uk_coupon_issue_event_user unique (coupon_event_id, user_id),
    constraint fk_coupon_issue_event foreign key (coupon_event_id) references coupon_event(id)
)
    ENGINE=InnoDB
    COMMENT='쿠폰 발급';

CREATE INDEX idx_coupon_issue_user_id on coupon_issue (user_id);
CREATE INDEX idx_coupon_issue_event_id on coupon_issue (coupon_event_id);

CREATE TABLE coupon_issue_request
(
    id                     BIGINT AUTO_INCREMENT    PRIMARY KEY,
    idempotency_key        VARCHAR(100) NOT NULL,
    coupon_event_id        BIGINT       NOT NULL,
    user_id                BIGINT       NOT NULL,
    request_status         VARCHAR(20)  NOT NULL,
    failure_reason         VARCHAR(50)              DEFAULT NULL,
    issued_coupon_issue_id BIGINT                   DEFAULT NULL,
    reused_count           INT          NOT NULL    DEFAULT 0,
    created_at             DATETIME(6) NOT NULL,
    updated_at             DATETIME(6) NOT NULL,

    CONSTRAINT uk_coupon_issue_request_event_user_key
        UNIQUE (coupon_event_id, user_id, idempotency_key)
);

CREATE INDEX idx_coupon_issue_request_event_status
    ON coupon_issue_request (coupon_event_id, request_status);

CREATE INDEX idx_coupon_issue_request_event_failure
    ON coupon_issue_request (coupon_event_id, failure_reason);
