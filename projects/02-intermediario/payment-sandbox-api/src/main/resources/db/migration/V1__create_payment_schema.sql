CREATE TABLE payments (
    id UUID NOT NULL,
    merchant_id VARCHAR(100) NOT NULL,
    merchant_reference VARCHAR(100) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT ck_payments_amount_positive CHECK (amount_minor > 0),
    CONSTRAINT ck_payments_currency CHECK (currency = 'BRL'),
    CONSTRAINT ck_payments_status CHECK (
        status IN ('PENDING', 'APPROVED', 'DECLINED', 'REFUNDED')
    )
);

CREATE TABLE payment_events (
    id UUID NOT NULL,
    payment_id UUID NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_payment_events PRIMARY KEY (id),
    CONSTRAINT fk_payment_events_payment
        FOREIGN KEY (payment_id)
        REFERENCES payments (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_payment_events_type CHECK (
        event_type IN (
            'PAYMENT_CREATED',
            'PAYMENT_APPROVED',
            'PAYMENT_DECLINED',
            'PAYMENT_REFUNDED'
        )
    ),
    CONSTRAINT ck_payment_events_from_status CHECK (
        from_status IS NULL
        OR from_status IN ('PENDING', 'APPROVED', 'DECLINED', 'REFUNDED')
    ),
    CONSTRAINT ck_payment_events_to_status CHECK (
        to_status IN ('PENDING', 'APPROVED', 'DECLINED', 'REFUNDED')
    )
);

CREATE TABLE refunds (
    id UUID NOT NULL,
    payment_id UUID NOT NULL,
    amount_minor BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_refunds PRIMARY KEY (id),
    CONSTRAINT fk_refunds_payment
        FOREIGN KEY (payment_id)
        REFERENCES payments (id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_refunds_payment UNIQUE (payment_id),
    CONSTRAINT ck_refunds_amount_positive CHECK (amount_minor > 0),
    CONSTRAINT ck_refunds_status CHECK (status = 'COMPLETED')
);

CREATE TABLE idempotency_records (
    id UUID NOT NULL,
    merchant_id VARCHAR(100) NOT NULL,
    operation_type VARCHAR(30) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    payment_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_idempotency_records PRIMARY KEY (id),
    CONSTRAINT fk_idempotency_records_payment
        FOREIGN KEY (payment_id)
        REFERENCES payments (id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_idempotency_records_merchant_operation_key
        UNIQUE (merchant_id, operation_type, idempotency_key),
    CONSTRAINT ck_idempotency_records_operation_type CHECK (
        operation_type IN ('CREATE_PAYMENT', 'CREATE_REFUND')
    )
);

CREATE INDEX idx_payments_merchant_created_at
    ON payments (merchant_id, created_at);

CREATE INDEX idx_payment_events_payment_occurred_at
    ON payment_events (payment_id, occurred_at);
