--
-- PostgreSQL database dump
--

\restrict 5QgQKibEke5SagZlgoesgRo9BfNvBVL1L2yqxXTW9ZQYxLvSwNbFWSye035ILrR

-- Dumped from database version 16.14
-- Dumped by pg_dump version 16.14

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--

-- *not* creating schema, since initdb creates it


--
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA public IS '';


--
-- Name: pgcrypto; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;


--
-- Name: EXTENSION pgcrypto; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION pgcrypto IS 'cryptographic functions';


--
-- Name: uuid-ossp; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;


--
-- Name: EXTENSION "uuid-ossp"; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION "uuid-ossp" IS 'generate universally unique identifiers (UUIDs)';


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: admin_user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admin_user (
    id bigint NOT NULL,
    email character varying(255) NOT NULL,
    password_hash character varying(255) NOT NULL,
    full_name character varying(128),
    telegram_user_id bigint,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: admin_user_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.admin_user_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: admin_user_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.admin_user_id_seq OWNED BY public.admin_user.id;


--
-- Name: app_meta; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_meta (
    key character varying(64) NOT NULL,
    value text NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: chat_message; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.chat_message (
    id bigint NOT NULL,
    assignment_id uuid NOT NULL,
    sender_role character varying(16) NOT NULL,
    sender_user_id bigint NOT NULL,
    body text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: chat_message_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.chat_message_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: chat_message_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.chat_message_id_seq OWNED BY public.chat_message.id;


--
-- Name: conversation_state; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.conversation_state (
    telegram_user_id bigint NOT NULL,
    state character varying(64) NOT NULL,
    data jsonb,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: delivery_assignment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.delivery_assignment (
    id uuid NOT NULL,
    order_id uuid NOT NULL,
    shipper_id bigint,
    status character varying(16) NOT NULL,
    assigned_at timestamp with time zone DEFAULT now() NOT NULL,
    accepted_at timestamp with time zone,
    rejected_at timestamp with time zone,
    started_at timestamp with time zone,
    delivered_at timestamp with time zone,
    cancelled_at timestamp with time zone
);


--
-- Name: location_ping; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.location_ping (
    id bigint NOT NULL,
    assignment_id uuid NOT NULL,
    lat numeric(10,7) NOT NULL,
    lng numeric(10,7) NOT NULL,
    accuracy numeric(8,2),
    heading numeric(5,2),
    recorded_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: location_ping_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.location_ping_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: location_ping_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.location_ping_id_seq OWNED BY public.location_ping.id;


--
-- Name: order_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.order_item (
    id bigint NOT NULL,
    order_id uuid NOT NULL,
    product_id bigint NOT NULL,
    quantity integer NOT NULL,
    unit_price numeric(12,2) NOT NULL,
    subtotal numeric(12,2) NOT NULL,
    CONSTRAINT order_item_quantity_check CHECK ((quantity > 0)),
    CONSTRAINT order_item_subtotal_check CHECK ((subtotal >= (0)::numeric)),
    CONSTRAINT order_item_unit_price_check CHECK ((unit_price >= (0)::numeric))
);


--
-- Name: order_item_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.order_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: order_item_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.order_item_id_seq OWNED BY public.order_item.id;


--
-- Name: orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.orders (
    id uuid NOT NULL,
    code character varying(32) NOT NULL,
    customer_id bigint NOT NULL,
    customer_name character varying(128),
    customer_phone character varying(32),
    pickup_lat numeric(10,7) NOT NULL,
    pickup_lng numeric(10,7) NOT NULL,
    delivery_address text NOT NULL,
    delivery_lat numeric(10,7) NOT NULL,
    delivery_lng numeric(10,7) NOT NULL,
    distance_km numeric(8,3) NOT NULL,
    subtotal numeric(12,2) NOT NULL,
    delivery_fee numeric(12,2) NOT NULL,
    total numeric(12,2) NOT NULL,
    payment_method character varying(16) NOT NULL,
    payment_status character varying(16) DEFAULT 'PENDING'::character varying NOT NULL,
    status character varying(16) DEFAULT 'PENDING'::character varying NOT NULL,
    note text,
    version integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    discount_products numeric(12,2) DEFAULT 0 NOT NULL,
    discount_shipping numeric(12,2) DEFAULT 0 NOT NULL,
    delivery_fee_original numeric(12,2) NOT NULL,
    shipper_commission numeric(12,2),
    CONSTRAINT chk_delivery_fee_original_nonneg CHECK ((delivery_fee_original >= (0)::numeric)),
    CONSTRAINT orders_delivery_fee_check CHECK ((delivery_fee >= (0)::numeric)),
    CONSTRAINT orders_discount_products_check CHECK ((discount_products >= (0)::numeric)),
    CONSTRAINT orders_discount_shipping_check CHECK ((discount_shipping >= (0)::numeric)),
    CONSTRAINT orders_distance_km_check CHECK ((distance_km >= (0)::numeric)),
    CONSTRAINT orders_shipper_commission_check CHECK (((shipper_commission IS NULL) OR (shipper_commission >= (0)::numeric))),
    CONSTRAINT orders_subtotal_check CHECK ((subtotal >= (0)::numeric)),
    CONSTRAINT orders_total_check CHECK ((total >= (0)::numeric))
);


--
-- Name: payment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payment (
    id uuid NOT NULL,
    order_id uuid NOT NULL,
    method character varying(16) NOT NULL,
    amount numeric(12,2) NOT NULL,
    status character varying(16) NOT NULL,
    vnp_txn_ref character varying(64),
    vnp_transaction_no character varying(64),
    vnp_response_code character varying(16),
    paid_at timestamp with time zone,
    version integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT payment_amount_check CHECK ((amount >= (0)::numeric))
);


--
-- Name: payment_transaction; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payment_transaction (
    id bigint NOT NULL,
    payment_id uuid NOT NULL,
    event_type character varying(16) NOT NULL,
    raw_payload jsonb NOT NULL,
    recorded_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: payment_transaction_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.payment_transaction_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: payment_transaction_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.payment_transaction_id_seq OWNED BY public.payment_transaction.id;


--
-- Name: processed_update; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.processed_update (
    update_id bigint NOT NULL,
    processed_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: product; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.product (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    price numeric(12,2) NOT NULL,
    image_url text,
    stock integer DEFAULT 0 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    category character varying(20) DEFAULT 'food'::character varying NOT NULL,
    CONSTRAINT product_category_check CHECK (((category)::text = ANY ((ARRAY['food'::character varying, 'drink'::character varying, 'dessert'::character varying])::text[]))),
    CONSTRAINT product_price_check CHECK ((price >= (0)::numeric)),
    CONSTRAINT product_stock_check CHECK ((stock >= 0))
);


--
-- Name: product_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.product_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: product_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.product_id_seq OWNED BY public.product.id;


--
-- Name: rating; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rating (
    id bigint NOT NULL,
    order_id uuid NOT NULL,
    customer_id bigint NOT NULL,
    shipper_id bigint NOT NULL,
    stars smallint NOT NULL,
    comment text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT rating_stars_check CHECK (((stars >= 1) AND (stars <= 5)))
);


--
-- Name: rating_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.rating_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: rating_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.rating_id_seq OWNED BY public.rating.id;


--
-- Name: refresh_token; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.refresh_token (
    id uuid NOT NULL,
    admin_user_id bigint NOT NULL,
    token_hash character varying(255) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: saved_address; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.saved_address (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    address text NOT NULL,
    lat numeric(10,7) NOT NULL,
    lng numeric(10,7) NOT NULL,
    use_count integer DEFAULT 1 NOT NULL,
    last_used_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: saved_address_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.saved_address_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: saved_address_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.saved_address_id_seq OWNED BY public.saved_address.id;


--
-- Name: shipper_ledger; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.shipper_ledger (
    id bigint NOT NULL,
    shipper_id bigint NOT NULL,
    entry_type character varying(32) NOT NULL,
    amount numeric(12,2) NOT NULL,
    order_id uuid,
    note text,
    created_by character varying(64) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT shipper_ledger_entry_type_check CHECK (((entry_type)::text = ANY ((ARRAY['COMMISSION'::character varying, 'COD_OWED'::character varying, 'SETTLEMENT_PAYOUT'::character varying, 'SETTLEMENT_DEPOSIT'::character varying])::text[])))
);


--
-- Name: shipper_ledger_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.shipper_ledger_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: shipper_ledger_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.shipper_ledger_id_seq OWNED BY public.shipper_ledger.id;


--
-- Name: shipper_profile; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.shipper_profile (
    user_id bigint NOT NULL,
    vehicle_type character varying(16) NOT NULL,
    license_plate character varying(16),
    current_state character varying(16) DEFAULT 'OFFLINE'::character varying NOT NULL,
    rating_avg numeric(3,2) DEFAULT 0.00 NOT NULL,
    rating_count integer DEFAULT 0 NOT NULL,
    total_deliveries integer DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: shipper_rating; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.shipper_rating (
    id bigint NOT NULL,
    order_id uuid NOT NULL,
    shipper_id bigint NOT NULL,
    customer_id bigint NOT NULL,
    stars smallint NOT NULL,
    comment text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT shipper_rating_stars_check CHECK (((stars >= 1) AND (stars <= 5)))
);


--
-- Name: shipper_rating_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.shipper_rating_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: shipper_rating_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.shipper_rating_id_seq OWNED BY public.shipper_rating.id;


--
-- Name: shop_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.shop_config (
    id smallint NOT NULL,
    name character varying(128) DEFAULT 'Shop Giao Hàng'::character varying NOT NULL,
    tagline character varying(256) DEFAULT 'Giao đồ ăn nhanh • Thanh toán dễ'::character varying NOT NULL,
    logo_url text,
    brand_primary character varying(7) DEFAULT '#D97706'::character varying NOT NULL,
    brand_secondary character varying(7) DEFAULT '#FB923C'::character varying NOT NULL,
    contact_phone character varying(32),
    contact_email character varying(128),
    opening_hours character varying(64) DEFAULT '08:00 - 22:00 hằng ngày'::character varying,
    pickup_lat numeric(10,7) DEFAULT 21.0285 NOT NULL,
    pickup_lng numeric(10,7) DEFAULT 105.8542 NOT NULL,
    pickup_address character varying(256) DEFAULT 'Shop default'::character varying NOT NULL,
    fee_base numeric(12,2) DEFAULT 15000 NOT NULL,
    fee_per_km numeric(12,2) DEFAULT 5000 NOT NULL,
    free_km numeric(8,3) DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    shipper_commission_pct numeric(5,2) DEFAULT 80.00 NOT NULL,
    CONSTRAINT shop_config_id_check CHECK ((id = 1)),
    CONSTRAINT shop_config_shipper_commission_pct_check CHECK (((shipper_commission_pct >= (0)::numeric) AND (shipper_commission_pct <= (100)::numeric)))
);


--
-- Name: status_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.status_history (
    id bigint NOT NULL,
    order_id uuid NOT NULL,
    from_status character varying(16),
    to_status character varying(16) NOT NULL,
    changed_by_user_id bigint,
    changed_at timestamp with time zone DEFAULT now() NOT NULL,
    note text
);


--
-- Name: status_history_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.status_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: status_history_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.status_history_id_seq OWNED BY public.status_history.id;


--
-- Name: telegram_user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.telegram_user (
    id bigint NOT NULL,
    username character varying(64),
    first_name character varying(128),
    last_name character varying(128),
    phone character varying(32),
    photo_url text,
    language_code character varying(8),
    is_blocked boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    customer_rating_avg numeric(3,2) DEFAULT 0.00 NOT NULL,
    customer_rating_count integer DEFAULT 0 NOT NULL
);


--
-- Name: user_role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_role (
    id bigint NOT NULL,
    telegram_user_id bigint NOT NULL,
    role character varying(16) NOT NULL,
    status character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
    assigned_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: user_role_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.user_role_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: user_role_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.user_role_id_seq OWNED BY public.user_role.id;


--
-- Name: voucher; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.voucher (
    id bigint NOT NULL,
    code character varying(32) NOT NULL,
    name character varying(128) NOT NULL,
    target character varying(16) NOT NULL,
    discount_type character varying(16) NOT NULL,
    discount_value numeric(12,2) NOT NULL,
    max_discount numeric(12,2),
    min_order_amount numeric(12,2) DEFAULT 0 NOT NULL,
    valid_from timestamp with time zone NOT NULL,
    valid_until timestamp with time zone NOT NULL,
    max_uses_total integer,
    max_uses_per_customer integer DEFAULT 1 NOT NULL,
    used_count integer DEFAULT 0 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT voucher_check CHECK ((valid_until > valid_from)),
    CONSTRAINT voucher_check1 CHECK ((((discount_type)::text = 'PERCENT'::text) OR (max_discount IS NULL))),
    CONSTRAINT voucher_discount_type_check CHECK (((discount_type)::text = ANY ((ARRAY['FIXED'::character varying, 'PERCENT'::character varying])::text[]))),
    CONSTRAINT voucher_discount_value_check CHECK ((discount_value > (0)::numeric)),
    CONSTRAINT voucher_max_discount_check CHECK (((max_discount IS NULL) OR (max_discount > (0)::numeric))),
    CONSTRAINT voucher_max_uses_per_customer_check CHECK ((max_uses_per_customer > 0)),
    CONSTRAINT voucher_max_uses_total_check CHECK (((max_uses_total IS NULL) OR (max_uses_total > 0))),
    CONSTRAINT voucher_min_order_amount_check CHECK ((min_order_amount >= (0)::numeric)),
    CONSTRAINT voucher_target_check CHECK (((target)::text = ANY ((ARRAY['SHIPPING'::character varying, 'PRODUCTS'::character varying])::text[])))
);


--
-- Name: voucher_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.voucher_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: voucher_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.voucher_id_seq OWNED BY public.voucher.id;


--
-- Name: voucher_redemption; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.voucher_redemption (
    id bigint NOT NULL,
    voucher_id bigint NOT NULL,
    order_id uuid NOT NULL,
    customer_id bigint NOT NULL,
    discount_applied numeric(12,2) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT voucher_redemption_discount_applied_check CHECK ((discount_applied >= (0)::numeric))
);


--
-- Name: voucher_redemption_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.voucher_redemption_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: voucher_redemption_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.voucher_redemption_id_seq OWNED BY public.voucher_redemption.id;


--
-- Name: admin_user id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_user ALTER COLUMN id SET DEFAULT nextval('public.admin_user_id_seq'::regclass);


--
-- Name: chat_message id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_message ALTER COLUMN id SET DEFAULT nextval('public.chat_message_id_seq'::regclass);


--
-- Name: location_ping id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.location_ping ALTER COLUMN id SET DEFAULT nextval('public.location_ping_id_seq'::regclass);


--
-- Name: order_item id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_item ALTER COLUMN id SET DEFAULT nextval('public.order_item_id_seq'::regclass);


--
-- Name: payment_transaction id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_transaction ALTER COLUMN id SET DEFAULT nextval('public.payment_transaction_id_seq'::regclass);


--
-- Name: product id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product ALTER COLUMN id SET DEFAULT nextval('public.product_id_seq'::regclass);


--
-- Name: rating id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rating ALTER COLUMN id SET DEFAULT nextval('public.rating_id_seq'::regclass);


--
-- Name: saved_address id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.saved_address ALTER COLUMN id SET DEFAULT nextval('public.saved_address_id_seq'::regclass);


--
-- Name: shipper_ledger id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shipper_ledger ALTER COLUMN id SET DEFAULT nextval('public.shipper_ledger_id_seq'::regclass);


--
-- Name: shipper_rating id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shipper_rating ALTER COLUMN id SET DEFAULT nextval('public.shipper_rating_id_seq'::regclass);


--
-- Name: status_history id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.status_history ALTER COLUMN id SET DEFAULT nextval('public.status_history_id_seq'::regclass);


--
-- Name: user_role id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_role ALTER COLUMN id SET DEFAULT nextval('public.user_role_id_seq'::regclass);


--
-- Name: voucher id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.voucher ALTER COLUMN id SET DEFAULT nextval('public.voucher_id_seq'::regclass);


--
-- Name: voucher_redemption id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.voucher_redemption ALTER COLUMN id SET DEFAULT nextval('public.voucher_redemption_id_seq'::regclass);


--
-- Data for Name: admin_user; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.admin_user (id, email, password_hash, full_name, telegram_user_id, is_active, created_at, updated_at) FROM stdin;
1	admin@shop.local	$2a$10$8tbM0mvZZFQuz9KVhA6lOOZQfM2GxHIgmTWXbcVQ2ml353wyiYktO	Shop Owner	\N	t	2026-08-13 07:43:35.334107+00	2026-08-13 07:43:35.334107+00
2	shop@example.com	$2a$10$OlsupF634zxKmzbpRaDIeOYK.VemZbX2XsLvn9nSKnRZ0fkw8dnU2	Demo Shop Owner	\N	t	2026-08-13 07:43:35.447743+00	2026-08-13 07:43:35.447743+00
\.


--
-- Data for Name: app_meta; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.app_meta (key, value, updated_at) FROM stdin;
schema_version	p0-baseline	2026-08-13 07:43:35.192265+00
initialized_at	2026-08-13 07:43:35.192265+00	2026-08-13 07:43:35.192265+00
\.


--
-- Data for Name: chat_message; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.chat_message (id, assignment_id, sender_role, sender_user_id, body, created_at) FROM stdin;
\.


--
-- Data for Name: conversation_state; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.conversation_state (telegram_user_id, state, data, updated_at) FROM stdin;
\.


--
-- Data for Name: delivery_assignment; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.delivery_assignment (id, order_id, shipper_id, status, assigned_at, accepted_at, rejected_at, started_at, delivered_at, cancelled_at) FROM stdin;
eacde66b-1a5e-4df2-88fc-4a1fc994dd8b	ef7351ae-470c-4e0a-b7b5-38c30103954e	9000000104	COMPLETED	2026-08-03 18:38:00+00	2026-08-03 18:40:00+00	\N	2026-08-03 18:51:00+00	2026-08-03 19:17:31.44+00	\N
344c0b5d-2329-483e-8956-991ebb3fc67e	cf485f7a-c0d8-4e2f-b684-ae10bb5c10d5	9000000102	COMPLETED	2026-07-23 20:08:00+00	2026-07-23 20:12:00+00	\N	2026-07-23 20:22:00+00	2026-07-23 21:00:50.4+00	\N
7eee6391-beed-44e1-8d17-b719eb36a038	95cd2744-9d5a-4846-a675-8eff8f3a0e23	9000000105	COMPLETED	2026-08-09 17:46:00+00	2026-08-09 17:48:00+00	\N	2026-08-09 17:55:00+00	2026-08-09 18:29:50.4+00	\N
0757c938-4527-4fe2-9351-8f09cbf61351	e5d75285-641a-404c-b22d-ed3ce396da96	9000000102	COMPLETED	2026-07-17 12:57:00+00	2026-07-17 12:58:00+00	\N	2026-07-17 13:09:00+00	2026-07-17 13:45:54+00	\N
95d68e73-3115-428d-909c-1d0e5d9f8264	07a155a6-f0b2-44f6-a4ad-82d05d35d3ec	9000000104	COMPLETED	2026-08-09 14:32:00+00	2026-08-09 14:34:00+00	\N	2026-08-09 14:40:00+00	2026-08-09 15:30:19.68+00	\N
eb420b1f-46dc-440c-9731-dc73f1ac5966	ccce1074-9c82-45c9-b742-a13a4aa32819	9000000102	COMPLETED	2026-08-07 16:28:00+00	2026-08-07 16:30:00+00	\N	2026-08-07 16:40:00+00	2026-08-07 17:11:46.8+00	\N
e4af6fd3-84a1-42bf-b104-87a58ff0361b	e522f7bf-5a5b-4403-9534-a7c52c639537	9000000104	COMPLETED	2026-07-26 20:26:00+00	2026-07-26 20:29:00+00	\N	2026-07-26 20:37:00+00	2026-07-26 21:05:38.4+00	\N
37ac7574-dc75-4fcd-a518-9e8b0b40e633	58b1c5da-2487-4332-a0f5-0406d45f6fb6	9000000102	COMPLETED	2026-08-04 08:39:00+00	2026-08-04 08:40:00+00	\N	2026-08-04 08:48:00+00	2026-08-04 09:14:37.44+00	\N
a53228ee-2c8b-4269-8984-940636eacf80	65d924e2-e7a9-4eb9-b2b1-9782031b146f	9000000101	COMPLETED	2026-07-22 10:41:00+00	2026-07-22 10:42:00+00	\N	2026-07-22 10:52:00+00	2026-07-22 11:41:02.4+00	\N
e519918e-3e3d-41f0-9105-5e451d4e52f3	4424346a-3dd9-4084-8fb6-20fc52b977ed	9000000104	COMPLETED	2026-07-26 18:00:00+00	2026-07-26 18:04:00+00	\N	2026-07-26 18:08:00+00	2026-07-26 18:48:24.24+00	\N
5f3c89f6-3c23-40a0-a084-629d1e14b9e0	ce9f4b64-25f0-4bf6-86d6-8bdb6af5f1f6	9000000102	COMPLETED	2026-07-14 19:13:00+00	2026-07-14 19:15:00+00	\N	2026-07-14 19:21:00+00	2026-07-14 19:55:45.12+00	\N
aaf3ef57-e712-484e-8f04-459bcff4ce1d	1371d53b-f0ec-49fc-ac00-64e5ddc7d66a	9000000105	COMPLETED	2026-07-28 16:26:00+00	2026-07-28 16:29:00+00	\N	2026-07-28 16:39:00+00	2026-07-28 17:33:49.44+00	\N
c1689d70-0eef-4e74-9718-c248adceba68	f539a609-fa6a-4e2b-85eb-6b1d8ed9e2fd	9000000104	COMPLETED	2026-07-19 21:05:00+00	2026-07-19 21:06:00+00	\N	2026-07-19 21:10:00+00	2026-07-19 21:52:05.52+00	\N
0dafca1e-747d-4a9c-967f-a087e03042cb	927ca6be-5d68-4abf-be79-c4b847b68df9	9000000102	COMPLETED	2026-08-08 16:04:00+00	2026-08-08 16:08:00+00	\N	2026-08-08 16:19:00+00	2026-08-08 17:13:53.28+00	\N
e2e79976-7a87-43cd-b66c-afbc5eec0455	c0aa3bb4-9c7e-4795-9e47-81328313713b	9000000104	COMPLETED	2026-07-12 09:58:00+00	2026-07-12 10:00:00+00	\N	2026-07-12 10:05:00+00	2026-07-12 10:47:06.48+00	\N
a65c6c3e-3f4b-49ba-aaa6-4b124d1b534c	09f4f7ac-58af-4dee-885a-12fda8414336	9000000105	COMPLETED	2026-08-01 09:03:00+00	2026-08-01 09:05:00+00	\N	2026-08-01 09:12:00+00	2026-08-01 09:50:11.04+00	\N
1a72422c-ecde-4ebc-8580-b41a08216594	7ddabb80-7b7a-4a4d-9c58-c0203c31fc46	9000000104	COMPLETED	2026-07-24 10:45:00+00	2026-07-24 10:47:00+00	\N	2026-07-24 10:52:00+00	2026-07-24 11:29:18.48+00	\N
9c5dfef7-bdc3-48ab-916f-aa253c010633	54e966e2-4009-4bf8-ad7f-68413dce74a1	9000000102	COMPLETED	2026-07-28 08:24:00+00	2026-07-28 08:27:00+00	\N	2026-07-28 08:36:00+00	2026-07-28 08:51:52.32+00	\N
00998ddd-0d81-450d-8b4d-b7dc825410a4	9d94bef9-0883-414b-8af1-279b195670fc	9000000101	COMPLETED	2026-08-09 15:43:00+00	2026-08-09 15:44:00+00	\N	2026-08-09 15:54:00+00	2026-08-09 16:26:24.72+00	\N
f1511458-0c5b-444b-ac6c-ca3cef4fc967	e3864da8-2daf-4740-b0d7-095804a16de2	9000000102	COMPLETED	2026-08-07 16:20:00+00	2026-08-07 16:23:00+00	\N	2026-08-07 16:27:00+00	2026-08-07 16:54:06+00	\N
35959bba-bec2-4f87-8aba-098aac1d75f5	7cc0b027-15f7-4145-90cf-835ec0553793	9000000101	COMPLETED	2026-08-03 10:44:00+00	2026-08-03 10:46:00+00	\N	2026-08-03 10:56:00+00	2026-08-03 11:43:27.36+00	\N
69e632cd-911c-4297-92d8-f4734c676e23	9cbcb6d1-8dbf-454a-9bc0-8f236b81c735	9000000102	COMPLETED	2026-07-24 18:09:00+00	2026-07-24 18:13:00+00	\N	2026-07-24 18:17:00+00	2026-07-24 18:52:33.12+00	\N
46ef6206-9af0-4a09-a176-d52815b8e809	12858304-2565-4efa-ac44-967e3754d616	9000000102	COMPLETED	2026-07-28 20:31:00+00	2026-07-28 20:35:00+00	\N	2026-07-28 20:44:00+00	2026-07-28 21:16:24.24+00	\N
d6fd1a52-a421-40e7-8636-1dd11fbc76b7	ba3e4076-c563-42b7-ba2b-4bfa199b57df	9000000101	COMPLETED	2026-07-18 17:32:00+00	2026-07-18 17:33:00+00	\N	2026-07-18 17:37:00+00	2026-07-18 18:06:14.64+00	\N
ca3c4b9d-7128-4566-a4ec-42da1da9f886	156d9c3b-a75a-4ffa-9c0c-65d5620cdcbc	9000000102	COMPLETED	2026-07-17 11:10:00+00	2026-07-17 11:11:00+00	\N	2026-07-17 11:21:00+00	2026-07-17 12:07:17.76+00	\N
7eb3177d-a0aa-4ffa-8fa1-5ed047d1aab4	feed5ba3-8777-4c41-abb3-d6379437e71c	9000000102	COMPLETED	2026-08-05 19:24:00+00	2026-08-05 19:26:00+00	\N	2026-08-05 19:33:00+00	2026-08-05 20:26:28.32+00	\N
0bfa6d19-2f77-4ea1-b58b-818207fc67a2	1aa7c754-bb0e-4e83-b947-9c17aeb65e53	9000000105	COMPLETED	2026-08-04 16:23:00+00	2026-08-04 16:24:00+00	\N	2026-08-04 16:33:00+00	2026-08-04 16:49:23.04+00	\N
5d7c5238-23a4-48f3-885a-ff9ddfa0ac45	41c5b85b-1961-499c-9ec6-2a61eb71865f	9000000105	COMPLETED	2026-07-31 08:54:00+00	2026-07-31 08:58:00+00	\N	2026-07-31 09:09:00+00	2026-07-31 09:42:23.76+00	\N
fc2c7faf-7da1-426c-a243-475298c61fc0	d5dbe7df-d262-49ef-b18f-13938b290901	9000000102	COMPLETED	2026-08-05 10:11:00+00	2026-08-05 10:15:00+00	\N	2026-08-05 10:22:00+00	2026-08-05 10:56:47.04+00	\N
62200e84-2438-4667-a759-bb0830074a8e	49b65a2e-19da-49a0-8964-cfc8ac32e005	9000000104	COMPLETED	2026-07-14 12:35:00+00	2026-07-14 12:37:00+00	\N	2026-07-14 12:46:00+00	2026-07-14 13:36:06+00	\N
c13aa0de-dd81-42bb-8bd2-cb037982cee0	bf09be0c-bf44-45e6-91de-7505423d777c	9000000101	COMPLETED	2026-07-13 19:50:00+00	2026-07-13 19:54:00+00	\N	2026-07-13 20:05:00+00	2026-07-13 20:47:40.32+00	\N
17597cb9-f10f-413a-9726-94bfe278fe0b	daf837c8-14c0-4eca-99a4-3fd9c9a3fa5c	9000000104	COMPLETED	2026-08-03 11:07:00+00	2026-08-03 11:08:00+00	\N	2026-08-03 11:14:00+00	2026-08-03 11:46:36+00	\N
77d39568-eb96-4a6e-bb43-139b2dd70b44	bb4e245f-83ae-4035-a2bf-7e2a411ceb6f	9000000102	COMPLETED	2026-07-29 11:23:00+00	2026-07-29 11:26:00+00	\N	2026-07-29 11:35:00+00	2026-07-29 12:02:33.84+00	\N
6b6b768f-76c7-4864-b75e-6b01e1ee812e	e8231aad-69e7-4a8e-869f-ab897492cc12	9000000104	COMPLETED	2026-07-26 17:08:00+00	2026-07-26 17:10:00+00	\N	2026-07-26 17:15:00+00	2026-07-26 17:34:54.24+00	\N
39d78bef-a0c0-4dff-aede-8a9da0834d6d	fe4ab8a9-5c7f-4d54-9515-5f1bab09fe60	9000000104	COMPLETED	2026-08-02 11:55:00+00	2026-08-02 11:57:00+00	\N	2026-08-02 12:04:00+00	2026-08-02 12:35:16.8+00	\N
c883063e-3c3c-41fe-b539-468371422a5f	e511e288-d7b5-44bf-a6fa-76f0dfca259a	9000000105	COMPLETED	2026-08-06 15:26:00+00	2026-08-06 15:29:00+00	\N	2026-08-06 15:38:00+00	2026-08-06 16:06:07.2+00	\N
a26fdab0-b8d4-4ead-a6bc-bfa48a6c54a3	f6eb20eb-aa59-45e8-80ee-eb835617bc04	9000000104	COMPLETED	2026-08-06 17:12:00+00	2026-08-06 17:14:00+00	\N	2026-08-06 17:23:00+00	2026-08-06 18:05:55.68+00	\N
0d26b430-b163-47bf-9189-c3a68f6ed15a	d107d669-b496-4dcc-ab4e-d2efb56f55cf	9000000101	COMPLETED	2026-08-12 14:23:00+00	2026-08-12 14:27:00+00	\N	2026-08-12 14:33:00+00	2026-08-12 15:08:41.76+00	\N
547ce5f4-ef0a-4d33-a268-5cb6adc62ba0	709e8b53-d695-4537-89e5-3cb2e021b8f8	9000000105	COMPLETED	2026-08-02 10:24:00+00	2026-08-02 10:25:00+00	\N	2026-08-02 10:29:00+00	2026-08-02 11:21:32.16+00	\N
cad84728-c5dd-4ff0-b3d6-80068913f3b7	7b0d565f-cfbf-451a-bba4-bb161c963675	9000000104	COMPLETED	2026-08-04 14:29:00+00	2026-08-04 14:31:00+00	\N	2026-08-04 14:38:00+00	2026-08-04 14:59:51.36+00	\N
7a7f0cfa-591d-4c5f-9450-837fbba978b6	22c37396-e5be-4d5a-9c8d-f0682c1ef38e	9000000102	COMPLETED	2026-07-12 13:17:00+00	2026-07-12 13:21:00+00	\N	2026-07-12 13:31:00+00	2026-07-12 14:23:27.12+00	\N
9fef7582-d6d7-4805-8629-eae6e4c3bba9	d5a97f80-1877-4c33-bffd-33567e09325f	9000000105	COMPLETED	2026-08-10 09:24:00+00	2026-08-10 09:26:00+00	\N	2026-08-10 09:31:00+00	2026-08-10 09:59:43.68+00	\N
fbb44a78-dd46-424e-8cac-5142fe3ba50f	7a2c6725-0fd4-4b6f-afae-4f4fd6de24c0	9000000101	COMPLETED	2026-08-12 11:37:00+00	2026-08-12 11:38:00+00	\N	2026-08-12 11:45:00+00	2026-08-12 12:23:36+00	\N
190212fc-75a0-4a94-83fe-084ffd17fea9	7cb9dd02-a9d6-4f9f-98d9-1a8a8f635e35	9000000102	COMPLETED	2026-07-14 11:18:00+00	2026-07-14 11:19:00+00	\N	2026-07-14 11:23:00+00	2026-07-14 12:01:12.72+00	\N
8a1b9693-833d-41eb-8b21-293c8372b7c9	3249dd29-eeb0-41b9-a360-66ad4c5890ea	9000000104	COMPLETED	2026-08-07 17:22:00+00	2026-08-07 17:25:00+00	\N	2026-08-07 17:33:00+00	2026-08-07 18:12:07.2+00	\N
fbb09d54-d6cc-417e-a7a7-c59a9c12adeb	18aa1028-1fb5-4643-b44e-1581931ff4e9	9000000105	COMPLETED	2026-08-04 13:15:00+00	2026-08-04 13:18:00+00	\N	2026-08-04 13:22:00+00	2026-08-04 14:01:04.8+00	\N
6743492b-a176-4ab6-8dd1-df77a0f9ded8	96d4dcd2-7a41-42b5-bbfa-2ffeda0243a1	9000000101	COMPLETED	2026-07-25 13:35:00+00	2026-07-25 13:37:00+00	\N	2026-07-25 13:46:00+00	2026-07-25 14:27:40.08+00	\N
bda38060-619e-4b3c-a462-67b0f0f8f9f3	f881d853-eb35-446c-bbb3-7838c420760d	9000000104	COMPLETED	2026-07-29 21:07:00+00	2026-07-29 21:11:00+00	\N	2026-07-29 21:22:00+00	2026-07-29 21:56:56.4+00	\N
ad14c2c0-3267-4ade-b973-79cd471d3956	b31179de-25cd-4900-afc8-748d58e1c685	9000000102	COMPLETED	2026-07-14 14:27:00+00	2026-07-14 14:28:00+00	\N	2026-07-14 14:38:00+00	2026-07-14 15:05:30.72+00	\N
e572e4ab-33f7-46e4-8d70-382c02978245	8d1f6598-0a74-4cde-955a-c81b5e646061	9000000105	COMPLETED	2026-08-10 08:30:00+00	2026-08-10 08:33:00+00	\N	2026-08-10 08:43:00+00	2026-08-10 09:13:03.36+00	\N
55d96c78-1110-4792-b1ef-4ea4968a5157	969e78cf-0b71-4f29-9fa7-bff1f6f01bdb	9000000101	COMPLETED	2026-07-14 19:22:00+00	2026-07-14 19:26:00+00	\N	2026-07-14 19:35:00+00	2026-07-14 20:00:28.32+00	\N
d55d48d2-f647-43f3-a589-70b13655960e	3a8f8ae6-68d1-40f8-8f29-7343da049169	9000000105	COMPLETED	2026-07-26 16:31:00+00	2026-07-26 16:34:00+00	\N	2026-07-26 16:45:00+00	2026-07-26 17:31:19.2+00	\N
ae319f07-ec0d-475e-bd28-66951beeeb15	dede1587-6e75-4485-b136-abcd2f2b232c	9000000105	COMPLETED	2026-07-29 18:03:00+00	2026-07-29 18:04:00+00	\N	2026-07-29 18:10:00+00	2026-07-29 18:24:14.64+00	\N
845214d7-c856-4d16-98ea-fb96d903c08f	641f4e85-e1f2-4ae0-a15e-7d12cae5727d	9000000105	COMPLETED	2026-07-28 11:43:00+00	2026-07-28 11:45:00+00	\N	2026-07-28 11:54:00+00	2026-07-28 12:23:11.04+00	\N
7f4f9f1b-7e4c-46ab-8cd5-7548cb01c4d0	7bff06a1-4b9d-435f-b0ce-f5dffadfcb91	9000000101	COMPLETED	2026-07-23 18:11:00+00	2026-07-23 18:15:00+00	\N	2026-07-23 18:24:00+00	2026-07-23 18:41:55.68+00	\N
3f0e5603-ef09-4ba9-b6f4-dd1eff602238	f38b2ebd-8cd5-4dd4-afbb-da238f64cd82	9000000105	COMPLETED	2026-07-12 17:43:00+00	2026-07-12 17:45:00+00	\N	2026-07-12 17:55:00+00	2026-07-12 18:50:36+00	\N
68f7c665-edaa-455d-a1da-60608cb59862	33d83c92-790e-4d27-bf3e-2acab5908b0b	9000000105	COMPLETED	2026-07-26 08:23:00+00	2026-07-26 08:24:00+00	\N	2026-07-26 08:35:00+00	2026-07-26 09:28:51.12+00	\N
f0dd7b73-32a3-4fd6-bcdb-c577f823df23	2d234eb9-1a84-4b8e-acec-a2efd1c7f3ad	9000000102	COMPLETED	2026-07-14 10:06:00+00	2026-07-14 10:07:00+00	\N	2026-07-14 10:13:00+00	2026-07-14 10:39:54.72+00	\N
be218220-27f5-43b1-8cd2-f470aa38d135	80df0f44-9540-40ad-b325-2d62da3f93af	9000000101	COMPLETED	2026-07-17 14:39:00+00	2026-07-17 14:43:00+00	\N	2026-07-17 14:51:00+00	2026-07-17 15:24:17.76+00	\N
ca304777-e2c1-4db6-8410-cc68a076445d	761362f6-9711-471b-8fbc-c897ef2d0c02	9000000104	COMPLETED	2026-07-13 18:39:00+00	2026-07-13 18:42:00+00	\N	2026-07-13 18:46:00+00	2026-07-13 19:32:47.28+00	\N
5088d8f7-9895-408e-a0f2-c020a1e3fe02	534f8b1a-1248-4d07-a315-0f7781edc41b	9000000101	COMPLETED	2026-07-24 14:19:00+00	2026-07-24 14:22:00+00	\N	2026-07-24 14:29:00+00	2026-07-24 15:10:30+00	\N
9cffdbcc-ca4e-4c57-bb4f-a7c30e46fe93	b3868885-f520-46ba-93fc-22ee350d22d0	9000000101	COMPLETED	2026-07-24 11:31:00+00	2026-07-24 11:33:00+00	\N	2026-07-24 11:37:00+00	2026-07-24 12:12:05.28+00	\N
9f474897-aeec-43f8-8703-0068ce3e1d0b	0010521a-9d1e-4b44-9eb8-e450dc153c52	9000000102	COMPLETED	2026-07-22 18:40:00+00	2026-07-22 18:41:00+00	\N	2026-07-22 18:50:00+00	2026-07-22 19:20:00.72+00	\N
39a0107e-d677-44d3-9c80-beb7e3dc8639	4ca4e8e4-fd31-45f5-8828-fdf505c3ffd4	9000000105	COMPLETED	2026-08-13 05:56:35.719639+00	2026-08-13 05:58:35.719639+00	\N	2026-08-13 06:07:35.719639+00	2026-08-13 06:59:00.919639+00	\N
\.


--
-- Data for Name: location_ping; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.location_ping (id, assignment_id, lat, lng, accuracy, heading, recorded_at) FROM stdin;
\.


--
-- Data for Name: order_item; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.order_item (id, order_id, product_id, quantity, unit_price, subtotal) FROM stdin;
31	ef7351ae-470c-4e0a-b7b5-38c30103954e	2	2	60000.00	120000.00
32	cf485f7a-c0d8-4e2f-b684-ae10bb5c10d5	4	2	65000.00	130000.00
33	95cd2744-9d5a-4846-a675-8eff8f3a0e23	16	2	15000.00	30000.00
34	95cd2744-9d5a-4846-a675-8eff8f3a0e23	9	1	30000.00	30000.00
35	95cd2744-9d5a-4846-a675-8eff8f3a0e23	12	1	40000.00	40000.00
36	e5d75285-641a-404c-b22d-ed3ce396da96	4	2	65000.00	130000.00
37	e5d75285-641a-404c-b22d-ed3ce396da96	9	3	30000.00	90000.00
38	07a155a6-f0b2-44f6-a4ad-82d05d35d3ec	20	3	25000.00	75000.00
39	07a155a6-f0b2-44f6-a4ad-82d05d35d3ec	16	2	15000.00	30000.00
40	07a155a6-f0b2-44f6-a4ad-82d05d35d3ec	15	2	60000.00	120000.00
41	69bb8ac8-0f19-42b1-9139-1093646bcbcf	11	1	50000.00	50000.00
42	ccce1074-9c82-45c9-b742-a13a4aa32819	14	1	40000.00	40000.00
43	ccce1074-9c82-45c9-b742-a13a4aa32819	6	3	35000.00	105000.00
44	e522f7bf-5a5b-4403-9534-a7c52c639537	20	1	25000.00	25000.00
45	e522f7bf-5a5b-4403-9534-a7c52c639537	13	3	45000.00	135000.00
46	e522f7bf-5a5b-4403-9534-a7c52c639537	14	1	40000.00	40000.00
47	58b1c5da-2487-4332-a0f5-0406d45f6fb6	19	3	30000.00	90000.00
48	58b1c5da-2487-4332-a0f5-0406d45f6fb6	3	3	25000.00	75000.00
49	58b1c5da-2487-4332-a0f5-0406d45f6fb6	8	1	45000.00	45000.00
50	65d924e2-e7a9-4eb9-b2b1-9782031b146f	15	2	60000.00	120000.00
51	4424346a-3dd9-4084-8fb6-20fc52b977ed	3	1	25000.00	25000.00
52	4424346a-3dd9-4084-8fb6-20fc52b977ed	19	2	30000.00	60000.00
53	4424346a-3dd9-4084-8fb6-20fc52b977ed	17	2	40000.00	80000.00
54	ce9f4b64-25f0-4bf6-86d6-8bdb6af5f1f6	17	3	40000.00	120000.00
55	ce9f4b64-25f0-4bf6-86d6-8bdb6af5f1f6	13	1	45000.00	45000.00
56	1371d53b-f0ec-49fc-ac00-64e5ddc7d66a	13	3	45000.00	135000.00
57	f539a609-fa6a-4e2b-85eb-6b1d8ed9e2fd	6	2	35000.00	70000.00
58	927ca6be-5d68-4abf-be79-c4b847b68df9	16	1	15000.00	15000.00
59	c0aa3bb4-9c7e-4795-9e47-81328313713b	13	3	45000.00	135000.00
60	c0aa3bb4-9c7e-4795-9e47-81328313713b	14	3	40000.00	120000.00
61	c0aa3bb4-9c7e-4795-9e47-81328313713b	8	3	45000.00	135000.00
62	09f4f7ac-58af-4dee-885a-12fda8414336	9	1	30000.00	30000.00
63	3f0cc202-05d2-4496-bef2-34626ed412aa	4	2	65000.00	130000.00
64	7ddabb80-7b7a-4a4d-9c58-c0203c31fc46	19	2	30000.00	60000.00
65	7ddabb80-7b7a-4a4d-9c58-c0203c31fc46	8	2	45000.00	90000.00
66	7ddabb80-7b7a-4a4d-9c58-c0203c31fc46	12	2	40000.00	80000.00
67	54e966e2-4009-4bf8-ad7f-68413dce74a1	14	3	40000.00	120000.00
68	54e966e2-4009-4bf8-ad7f-68413dce74a1	11	3	50000.00	150000.00
69	9d94bef9-0883-414b-8af1-279b195670fc	18	3	30000.00	90000.00
70	e3864da8-2daf-4740-b0d7-095804a16de2	17	3	40000.00	120000.00
71	e3864da8-2daf-4740-b0d7-095804a16de2	20	2	25000.00	50000.00
72	7cc0b027-15f7-4145-90cf-835ec0553793	12	2	40000.00	80000.00
73	7cc0b027-15f7-4145-90cf-835ec0553793	10	3	50000.00	150000.00
74	9cbcb6d1-8dbf-454a-9bc0-8f236b81c735	11	1	50000.00	50000.00
75	12858304-2565-4efa-ac44-967e3754d616	6	3	35000.00	105000.00
76	ba3e4076-c563-42b7-ba2b-4bfa199b57df	7	1	20000.00	20000.00
77	ba3e4076-c563-42b7-ba2b-4bfa199b57df	9	1	30000.00	30000.00
78	156d9c3b-a75a-4ffa-9c0c-65d5620cdcbc	2	3	60000.00	180000.00
79	156d9c3b-a75a-4ffa-9c0c-65d5620cdcbc	11	3	50000.00	150000.00
80	feed5ba3-8777-4c41-abb3-d6379437e71c	3	3	25000.00	75000.00
81	1aa7c754-bb0e-4e83-b947-9c17aeb65e53	8	2	45000.00	90000.00
82	1aa7c754-bb0e-4e83-b947-9c17aeb65e53	20	1	25000.00	25000.00
83	41c5b85b-1961-499c-9ec6-2a61eb71865f	1	1	55000.00	55000.00
84	41c5b85b-1961-499c-9ec6-2a61eb71865f	7	2	20000.00	40000.00
85	41c5b85b-1961-499c-9ec6-2a61eb71865f	9	1	30000.00	30000.00
86	d5dbe7df-d262-49ef-b18f-13938b290901	1	2	55000.00	110000.00
87	49b65a2e-19da-49a0-8964-cfc8ac32e005	4	1	65000.00	65000.00
88	49b65a2e-19da-49a0-8964-cfc8ac32e005	19	2	30000.00	60000.00
89	49b65a2e-19da-49a0-8964-cfc8ac32e005	2	1	60000.00	60000.00
90	bf09be0c-bf44-45e6-91de-7505423d777c	5	2	70000.00	140000.00
91	bf09be0c-bf44-45e6-91de-7505423d777c	4	3	65000.00	195000.00
92	bf09be0c-bf44-45e6-91de-7505423d777c	7	3	20000.00	60000.00
93	daf837c8-14c0-4eca-99a4-3fd9c9a3fa5c	17	1	40000.00	40000.00
94	daf837c8-14c0-4eca-99a4-3fd9c9a3fa5c	13	3	45000.00	135000.00
95	daf837c8-14c0-4eca-99a4-3fd9c9a3fa5c	6	3	35000.00	105000.00
96	bb4e245f-83ae-4035-a2bf-7e2a411ceb6f	5	1	70000.00	70000.00
97	bb4e245f-83ae-4035-a2bf-7e2a411ceb6f	14	2	40000.00	80000.00
98	e8231aad-69e7-4a8e-869f-ab897492cc12	7	2	20000.00	40000.00
99	fe4ab8a9-5c7f-4d54-9515-5f1bab09fe60	10	1	50000.00	50000.00
100	fe4ab8a9-5c7f-4d54-9515-5f1bab09fe60	13	1	45000.00	45000.00
101	fe4ab8a9-5c7f-4d54-9515-5f1bab09fe60	8	2	45000.00	90000.00
102	e511e288-d7b5-44bf-a6fa-76f0dfca259a	6	2	35000.00	70000.00
103	e511e288-d7b5-44bf-a6fa-76f0dfca259a	12	1	40000.00	40000.00
104	e511e288-d7b5-44bf-a6fa-76f0dfca259a	3	3	25000.00	75000.00
105	f6eb20eb-aa59-45e8-80ee-eb835617bc04	10	1	50000.00	50000.00
106	f6eb20eb-aa59-45e8-80ee-eb835617bc04	13	1	45000.00	45000.00
107	f6eb20eb-aa59-45e8-80ee-eb835617bc04	5	1	70000.00	70000.00
108	d107d669-b496-4dcc-ab4e-d2efb56f55cf	1	2	55000.00	110000.00
109	d107d669-b496-4dcc-ab4e-d2efb56f55cf	9	1	30000.00	30000.00
110	d107d669-b496-4dcc-ab4e-d2efb56f55cf	2	2	60000.00	120000.00
111	709e8b53-d695-4537-89e5-3cb2e021b8f8	17	2	40000.00	80000.00
112	7b0d565f-cfbf-451a-bba4-bb161c963675	3	1	25000.00	25000.00
113	7b0d565f-cfbf-451a-bba4-bb161c963675	13	2	45000.00	90000.00
114	7b0d565f-cfbf-451a-bba4-bb161c963675	19	3	30000.00	90000.00
115	22c37396-e5be-4d5a-9c8d-f0682c1ef38e	1	3	55000.00	165000.00
116	22c37396-e5be-4d5a-9c8d-f0682c1ef38e	12	3	40000.00	120000.00
117	d5a97f80-1877-4c33-bffd-33567e09325f	2	1	60000.00	60000.00
118	d5a97f80-1877-4c33-bffd-33567e09325f	15	1	60000.00	60000.00
119	d5a97f80-1877-4c33-bffd-33567e09325f	7	2	20000.00	40000.00
120	7a2c6725-0fd4-4b6f-afae-4f4fd6de24c0	13	3	45000.00	135000.00
121	7a2c6725-0fd4-4b6f-afae-4f4fd6de24c0	9	1	30000.00	30000.00
122	7a2c6725-0fd4-4b6f-afae-4f4fd6de24c0	17	1	40000.00	40000.00
123	7cb9dd02-a9d6-4f9f-98d9-1a8a8f635e35	19	2	30000.00	60000.00
124	7cb9dd02-a9d6-4f9f-98d9-1a8a8f635e35	17	3	40000.00	120000.00
125	3249dd29-eeb0-41b9-a360-66ad4c5890ea	2	1	60000.00	60000.00
126	3249dd29-eeb0-41b9-a360-66ad4c5890ea	5	1	70000.00	70000.00
127	3249dd29-eeb0-41b9-a360-66ad4c5890ea	13	3	45000.00	135000.00
128	18aa1028-1fb5-4643-b44e-1581931ff4e9	1	2	55000.00	110000.00
129	96d4dcd2-7a41-42b5-bbfa-2ffeda0243a1	5	3	70000.00	210000.00
130	f881d853-eb35-446c-bbb3-7838c420760d	12	3	40000.00	120000.00
131	f881d853-eb35-446c-bbb3-7838c420760d	16	3	15000.00	45000.00
132	b31179de-25cd-4900-afc8-748d58e1c685	15	1	60000.00	60000.00
133	8d1f6598-0a74-4cde-955a-c81b5e646061	10	2	50000.00	100000.00
134	8d1f6598-0a74-4cde-955a-c81b5e646061	8	3	45000.00	135000.00
135	8d1f6598-0a74-4cde-955a-c81b5e646061	5	1	70000.00	70000.00
136	969e78cf-0b71-4f29-9fa7-bff1f6f01bdb	13	2	45000.00	90000.00
137	969e78cf-0b71-4f29-9fa7-bff1f6f01bdb	20	2	25000.00	50000.00
138	3a8f8ae6-68d1-40f8-8f29-7343da049169	15	2	60000.00	120000.00
139	dede1587-6e75-4485-b136-abcd2f2b232c	16	1	15000.00	15000.00
140	dede1587-6e75-4485-b136-abcd2f2b232c	8	1	45000.00	45000.00
141	dede1587-6e75-4485-b136-abcd2f2b232c	18	1	30000.00	30000.00
142	641f4e85-e1f2-4ae0-a15e-7d12cae5727d	13	2	45000.00	90000.00
143	641f4e85-e1f2-4ae0-a15e-7d12cae5727d	20	2	25000.00	50000.00
144	7bff06a1-4b9d-435f-b0ce-f5dffadfcb91	8	1	45000.00	45000.00
145	7bff06a1-4b9d-435f-b0ce-f5dffadfcb91	10	2	50000.00	100000.00
146	7bff06a1-4b9d-435f-b0ce-f5dffadfcb91	4	2	65000.00	130000.00
147	f38b2ebd-8cd5-4dd4-afbb-da238f64cd82	10	1	50000.00	50000.00
148	33d83c92-790e-4d27-bf3e-2acab5908b0b	2	3	60000.00	180000.00
149	33d83c92-790e-4d27-bf3e-2acab5908b0b	13	2	45000.00	90000.00
150	33d83c92-790e-4d27-bf3e-2acab5908b0b	5	2	70000.00	140000.00
151	2d234eb9-1a84-4b8e-acec-a2efd1c7f3ad	14	1	40000.00	40000.00
152	2d234eb9-1a84-4b8e-acec-a2efd1c7f3ad	9	3	30000.00	90000.00
153	80df0f44-9540-40ad-b325-2d62da3f93af	4	3	65000.00	195000.00
154	80df0f44-9540-40ad-b325-2d62da3f93af	3	2	25000.00	50000.00
155	80df0f44-9540-40ad-b325-2d62da3f93af	9	2	30000.00	60000.00
156	c18f08f0-88a4-48e2-a05d-9e0026c11360	12	3	40000.00	120000.00
157	761362f6-9711-471b-8fbc-c897ef2d0c02	15	2	60000.00	120000.00
158	761362f6-9711-471b-8fbc-c897ef2d0c02	20	2	25000.00	50000.00
159	761362f6-9711-471b-8fbc-c897ef2d0c02	17	3	40000.00	120000.00
160	534f8b1a-1248-4d07-a315-0f7781edc41b	17	2	40000.00	80000.00
161	b3868885-f520-46ba-93fc-22ee350d22d0	3	1	25000.00	25000.00
162	0010521a-9d1e-4b44-9eb8-e450dc153c52	1	2	55000.00	110000.00
163	798a9bb7-edac-4b3c-b7d8-a4e7b9f4bcba	10	1	50000.00	50000.00
164	798a9bb7-edac-4b3c-b7d8-a4e7b9f4bcba	6	1	35000.00	35000.00
165	798a9bb7-edac-4b3c-b7d8-a4e7b9f4bcba	11	3	50000.00	150000.00
166	ca4b3cad-5556-493f-9e9d-42d73d6a0f01	7	3	20000.00	60000.00
167	ca4b3cad-5556-493f-9e9d-42d73d6a0f01	17	3	40000.00	120000.00
168	ca4b3cad-5556-493f-9e9d-42d73d6a0f01	19	2	30000.00	60000.00
169	261c9030-2493-4cd2-bf6d-bd69fccca4f7	12	3	40000.00	120000.00
170	4ca4e8e4-fd31-45f5-8828-fdf505c3ffd4	3	1	25000.00	25000.00
\.


--
-- Data for Name: orders; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.orders (id, code, customer_id, customer_name, customer_phone, pickup_lat, pickup_lng, delivery_address, delivery_lat, delivery_lng, distance_km, subtotal, delivery_fee, total, payment_method, payment_status, status, note, version, created_at, updated_at, discount_products, discount_shipping, delivery_fee_original, shipper_commission) FROM stdin;
ef7351ae-470c-4e0a-b7b5-38c30103954e	DH20260803-FB399	9000001008	Ngô Tâm	0912456008	21.0285000	105.8542000	302 Cầu Giấy, Dịch Vọng, Cầu Giấy, Hà Nội	21.0334023	105.7938898	4.131	120000.00	35000.00	155000.00	COD	SUCCESS	DELIVERED	\N	0	2026-08-03 18:20:00+00	2026-08-03 19:17:31.44+00	0.00	0.00	35000.00	28000.00
cf485f7a-c0d8-4e2f-b684-ae10bb5c10d5	DH20260723-691ED	9000001006	Đỗ Lan	0912456006	21.0285000	105.8542000	33 Giảng Võ, Cát Linh, Đống Đa, Hà Nội	21.0284892	105.8291502	4.460	130000.00	35000.00	165000.00	COD	SUCCESS	DELIVERED	\N	0	2026-07-23 19:55:00+00	2026-07-23 21:00:50.4+00	0.00	0.00	35000.00	28000.00
95cd2744-9d5a-4846-a675-8eff8f3a0e23	DH20260809-C6460	9000001007	Bùi Huy	0912456007	21.0285000	105.8542000	19 Duy Tân, Dịch Vọng Hậu, Cầu Giấy, Hà Nội	21.0294954	105.7830067	3.210	100000.00	30000.00	130000.00	COD	SUCCESS	DELIVERED	\N	0	2026-08-09 17:41:00+00	2026-08-09 18:29:50.4+00	0.00	0.00	30000.00	24000.00
e5d75285-641a-404c-b22d-ed3ce396da96	DH20260717-40BC6	9000001001	Trần Hùng	0912456001	21.0285000	105.8542000	68 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội	21.0125064	105.7974468	5.225	220000.00	40000.00	260000.00	VNPAY	SUCCESS	DELIVERED	\N	0	2026-07-17 12:43:00+00	2026-07-17 13:45:54+00	0.00	0.00	40000.00	32000.00
07a155a6-f0b2-44f6-a4ad-82d05d35d3ec	DH20260809-042C2	9000000002	Trần Bình	0901234002	21.0285000	105.8542000	89 Hoàng Quốc Việt, Nghĩa Đô, Cầu Giấy, Hà Nội	21.0443390	105.7928553	7.832	225000.00	50000.00	275000.00	COD	SUCCESS	DELIVERED	\N	0	2026-08-09 14:16:00+00	2026-08-09 15:30:19.68+00	0.00	0.00	50000.00	40000.00
69bb8ac8-0f19-42b1-9139-1093646bcbcf	DH20260725-50986	9000000003	Lê Chi	0901234003	21.0285000	105.8542000	210 Nguyễn Trãi, Thượng Đình, Thanh Xuân, Hà Nội	20.9934479	105.8120111	3.731	50000.00	30000.00	80000.00	COD	PENDING	CANCELLED	Gọi trước khi giao giúp em	0	2026-07-25 12:57:00+00	2026-07-25 13:13:00+00	0.00	0.00	30000.00	\N
ccce1074-9c82-45c9-b742-a13a4aa32819	DH20260807-FECA4	9000001009	Đặng Quỳnh	0912456009	21.0285000	105.8542000	120 Bạch Mai, Cầu Dền, Hai Bà Trưng, Hà Nội	21.0015528	105.8499786	4.445	145000.00	35000.00	180000.00	COD	SUCCESS	DELIVERED	Thêm ớt	0	2026-08-07 16:21:00+00	2026-08-07 17:11:46.8+00	0.00	0.00	35000.00	28000.00
e522f7bf-5a5b-4403-9534-a7c52c639537	DH20260726-C8988	9000001006	Đỗ Lan	0912456006	21.0285000	105.8542000	46 Hàng Bông, Hàng Gai, Hoàn Kiếm, Hà Nội	21.0307478	105.8481563	1.160	200000.00	20000.00	220000.00	VNPAY	SUCCESS	DELIVERED	\N	0	2026-07-26 20:08:00+00	2026-07-26 21:05:38.4+00	0.00	0.00	20000.00	16000.00
58b1c5da-2487-4332-a0f5-0406d45f6fb6	DH20260804-73729	9000001005	Hoàng Nam	0912456005	21.0285000	105.8542000	68 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội	21.0122274	105.7964515	1.656	210000.00	20000.00	230000.00	COD	SUCCESS	DELIVERED	Giao giờ trưa giúp mình	0	2026-08-04 08:29:00+00	2026-08-04 09:14:37.44+00	0.00	0.00	20000.00	16000.00
65d924e2-e7a9-4eb9-b2b1-9782031b146f	DH20260722-C7443	9000001006	Đỗ Lan	0912456006	21.0285000	105.8542000	191 Bà Triệu, Lê Đại Hành, Hai Bà Trưng, Hà Nội	21.0121236	105.8491264	7.260	120000.00	50000.00	170000.00	VNPAY	SUCCESS	DELIVERED	\N	0	2026-07-22 10:29:00+00	2026-07-22 11:41:02.4+00	0.00	0.00	50000.00	40000.00
4424346a-3dd9-4084-8fb6-20fc52b977ed	DH20260726-C891B	9000001008	Ngô Tâm	0912456008	21.0285000	105.8542000	15 Kim Mã, Ngọc Khánh, Ba Đình, Hà Nội	21.0320507	105.8196598	6.351	165000.00	45000.00	210000.00	COD	SUCCESS	DELIVERED	\N	0	2026-07-26 17:43:00+00	2026-07-26 18:48:24.24+00	0.00	0.00	45000.00	36000.00
ce9f4b64-25f0-4bf6-86d6-8bdb6af5f1f6	DH20260714-57455	9000001003	Lê Đức	0912456003	21.0285000	105.8542000	68 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội	21.0127929	105.7972210	2.688	165000.00	25000.00	190000.00	COD	SUCCESS	DELIVERED	\N	0	2026-07-14 19:05:00+00	2026-07-14 19:55:45.12+00	0.00	0.00	25000.00	20000.00
1371d53b-f0ec-49fc-ac00-64e5ddc7d66a	DH20260728-BCD65	9000001003	Lê Đức	0912456003	21.0285000	105.8542000	46 Hàng Bông, Hàng Gai, Hoàn Kiếm, Hà Nội	21.0301957	105.8485435	7.956	135000.00	50000.00	185000.00	VNPAY	SUCCESS	DELIVERED	\N	0	2026-07-28 16:21:00+00	2026-07-28 17:33:49.44+00	0.00	0.00	50000.00	40000.00
f539a609-fa6a-4e2b-85eb-6b1d8ed9e2fd	DH20260719-3F4B6	9000001009	Đặng Quỳnh	0912456009	21.0285000	105.8542000	25 Láng Hạ, Thành Công, Ba Đình, Hà Nội	21.0170940	105.8129882	6.523	70000.00	45000.00	115000.00	VNPAY	SUCCESS	DELIVERED	\N	0	2026-07-19 20:52:00+00	2026-07-19 21:52:05.52+00	0.00	0.00	45000.00	36000.00
927ca6be-5d68-4abf-be79-c4b847b68df9	DH20260808-F69B6	9000001009	Đặng Quỳnh	0912456009	21.0285000	105.8542000	78 Tây Sơn, Quang Trung, Đống Đa, Hà Nội	21.0087953	105.8232861	7.972	15000.00	50000.00	65000.00	COD	SUCCESS	DELIVERED	Giao giờ trưa giúp mình	0	2026-08-08 15:51:00+00	2026-08-08 17:13:53.28+00	0.00	0.00	50000.00	40000.00
c0aa3bb4-9c7e-4795-9e47-81328313713b	DH20260712-25207	9000001003	Lê Đức	0912456003	21.0285000	105.8542000	210 Nguyễn Trãi, Thượng Đình, Thanh Xuân, Hà Nội	20.9937827	105.8112082	5.277	390000.00	40000.00	430000.00	VNPAY	SUCCESS	DELIVERED	\N	0	2026-07-12 09:50:00+00	2026-07-12 10:47:06.48+00	0.00	0.00	40000.00	32000.00
09f4f7ac-58af-4dee-885a-12fda8414336	DH20260801-6B2DB	9000001009	Đặng Quỳnh	0912456009	21.0285000	105.8542000	285 Đội Cấn, Liễu Giai, Ba Đình, Hà Nội	21.0364373	105.8216208	5.046	30000.00	40000.00	70000.00	VNPAY	SUCCESS	DELIVERED	Giao giờ trưa giúp mình	0	2026-08-01 08:56:00+00	2026-08-01 09:50:11.04+00	0.00	0.00	40000.00	32000.00
3f0cc202-05d2-4496-bef2-34626ed412aa	DH20260802-8226A	9000001001	Trần Hùng	0912456001	21.0285000	105.8542000	19 Duy Tân, Dịch Vọng Hậu, Cầu Giấy, Hà Nội	21.0295434	105.7838323	1.178	130000.00	20000.00	150000.00	COD	PENDING	CANCELLED	\N	0	2026-08-02 18:47:00+00	2026-08-02 19:06:00+00	0.00	0.00	20000.00	\N
7ddabb80-7b7a-4a4d-9c58-c0203c31fc46	DH20260724-E8662	9000001007	Bùi Huy	0912456007	21.0285000	105.8542000	285 Đội Cấn, Liễu Giai, Ba Đình, Hà Nội	21.0375453	105.8216534	4.077	230000.00	35000.00	265000.00	COD	SUCCESS	DELIVERED	\N	0	2026-07-24 10:29:00+00	2026-07-24 11:29:18.48+00	0.00	0.00	35000.00	28000.00
54e966e2-4009-4bf8-ad7f-68413dce74a1	DH20260728-33599	9000000001	Nguyễn An	0901234001	21.0285000	105.8542000	89 Hoàng Quốc Việt, Nghĩa Đô, Cầu Giấy, Hà Nội	21.0460132	105.7934037	1.218	270000.00	20000.00	290000.00	COD	SUCCESS	DELIVERED	Giao giờ trưa giúp mình	0	2026-07-28 08:11:00+00	2026-07-28 08:51:52.32+00	0.00	0.00	20000.00	16000.00
9d94bef9-0883-414b-8af1-279b195670fc	DH20260809-4CFEA	9000001005	Hoàng Nam	0912456005	21.0285000	105.8542000	68 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội	21.0121084	105.7961936	3.603	90000.00	30000.00	120000.00	VNPAY	SUCCESS	DELIVERED	\N	0	2026-08-09 15:30:00+00	2026-08-09 16:26:24.72+00	0.00	0.00	30000.00	24000.00
e3864da8-2daf-4740-b0d7-095804a16de2	DH20260807-82F91	9000000001	Nguyễn An	0901234001	21.0285000	105.8542000	210 Nguyễn Trãi, Thượng Đình, Thanh Xuân, Hà Nội	20.9939922	105.8110587	2.275	170000.00	25000.00	195000.00	COD	SUCCESS	DELIVERED	\N	0	2026-08-07 16:02:00+00	2026-08-07 16:54:06+00	0.00	0.00	25000.00	20000.00
7cc0b027-15f7-4145-90cf-835ec0553793	DH20260803-60E29	9000001004	Vũ Mai	0912456004	21.0285000	105.8542000	46 Hàng Bông, Hàng Gai, Hoàn Kiếm, Hà Nội	21.0298089	105.8480464	6.864	230000.00	45000.00	275000.00	COD	SUCCESS	DELIVERED	\N	0	2026-08-03 10:34:00+00	2026-08-03 11:43:27.36+00	0.00	0.00	45000.00	36000.00
9cbcb6d1-8dbf-454a-9bc0-8f236b81c735	DH20260724-0E6D0	9000001007	Bùi Huy	0912456007	21.0285000	105.8542000	46 Hàng Bông, Hàng Gai, Hoàn Kiếm, Hà Nội	21.0298826	105.8484115	6.138	50000.00	45000.00	95000.00	COD	SUCCESS	DELIVERED	\N	0	2026-07-24 17:56:00+00	2026-07-24 18:52:33.12+00	0.00	0.00	45000.00	36000.00
12858304-2565-4efa-ac44-967e3754d616	DH20260728-72A48	9000001006	Đỗ Lan	0912456006	21.0285000	105.8542000	19 Duy Tân, Dịch Vọng Hậu, Cầu Giấy, Hà Nội	21.0281244	105.7830835	2.851	105000.00	25000.00	130000.00	COD	SUCCESS	DELIVERED	Không hành	0	2026-07-28 20:17:00+00	2026-07-28 21:16:24.24+00	0.00	0.00	25000.00	20000.00
ba3e4076-c563-42b7-ba2b-4bfa199b57df	DH20260718-0C407	9000001010	Dương Khánh	0912456010	21.0285000	105.8542000	54 Nguyễn Chí Thanh, Láng Thượng, Đống Đa, Hà Nội	21.0223222	105.8092167	2.811	50000.00	25000.00	75000.00	VNPAY	SUCCESS	DELIVERED	\N	0	2026-07-18 17:25:00+00	2026-07-18 18:06:14.64+00	0.00	0.00	25000.00	20000.00
156d9c3b-a75a-4ffa-9c0c-65d5620cdcbc	DH20260717-14BA6	9000001008	Ngô Tâm	0912456008	21.0285000	105.8542000	33 Giảng Võ, Cát Linh, Đống Đa, Hà Nội	21.0285817	105.8278321	5.574	330000.00	40000.00	370000.00	COD	SUCCESS	DELIVERED	\N	0	2026-07-17 11:00:00+00	2026-07-17 12:07:17.76+00	0.00	0.00	40000.00	32000.00
feed5ba3-8777-4c41-abb3-d6379437e71c	DH20260805-296FE	9000001007	Bùi Huy	0912456007	21.0285000	105.8542000	33 Giảng Võ, Cát Linh, Đống Đa, Hà Nội	21.0276880	105.8293495	7.618	75000.00	50000.00	125000.00	COD	SUCCESS	DELIVERED	Để ở sảnh chung cư, gọi em xuống lấy	0	2026-08-05 19:10:00+00	2026-08-05 20:26:28.32+00	0.00	0.00	50000.00	40000.00
1aa7c754-bb0e-4e83-b947-9c17aeb65e53	DH20260804-B8BAC	9000001005	Hoàng Nam	0912456005	21.0285000	105.8542000	68 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội	21.0123106	105.7969577	1.346	115000.00	20000.00	135000.00	COD	SUCCESS	DELIVERED	\N	0	2026-08-04 16:17:00+00	2026-08-04 16:49:23.04+00	0.00	0.00	20000.00	16000.00
41c5b85b-1961-499c-9ec6-2a61eb71865f	DH20260731-705F5	9000001010	Dương Khánh	0912456010	21.0285000	105.8542000	191 Bà Triệu, Lê Đại Hành, Hai Bà Trưng, Hà Nội	21.0118213	105.8485724	3.099	125000.00	30000.00	155000.00	COD	SUCCESS	DELIVERED	\N	0	2026-07-31 08:40:00+00	2026-07-31 09:42:23.76+00	0.00	0.00	30000.00	24000.00
d5dbe7df-d262-49ef-b18f-13938b290901	DH20260805-B4880	9000001006	Đỗ Lan	0912456006	21.0285000	105.8542000	68 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội	21.0114644	105.7957989	6.196	110000.00	45000.00	155000.00	VNPAY	SUCCESS	DELIVERED	\N	0	2026-08-05 10:01:00+00	2026-08-05 10:56:47.04+00	0.00	0.00	45000.00	36000.00
49b65a2e-19da-49a0-8964-cfc8ac32e005	DH20260714-50EF4	9000001004	Vũ Mai	0912456004	21.0285000	105.8542000	302 Cầu Giấy, Dịch Vọng, Cầu Giấy, Hà Nội	21.0339846	105.7938043	7.025	185000.00	50000.00	235000.00	VNPAY	SUCCESS	DELIVERED	Để ở sảnh chung cư, gọi em xuống lấy	0	2026-07-14 12:22:00+00	2026-07-14 13:36:06+00	0.00	0.00	50000.00	40000.00
bf09be0c-bf44-45e6-91de-7505423d777c	DH20260713-A4C24	9000001001	Trần Hùng	0912456001	21.0285000	105.8542000	191 Bà Triệu, Lê Đại Hành, Hai Bà Trưng, Hà Nội	21.0126263	105.8501884	7.418	395000.00	50000.00	445000.00	VNPAY	SUCCESS	DELIVERED	\N	0	2026-07-13 19:43:00+00	2026-07-13 20:47:40.32+00	0.00	0.00	50000.00	40000.00
daf837c8-14c0-4eca-99a4-3fd9c9a3fa5c	DH20260803-E992E	9000001006	Đỗ Lan	0912456006	21.0285000	105.8542000	15 Kim Mã, Ngọc Khánh, Ba Đình, Hà Nội	21.0301413	105.8184224	3.650	280000.00	30000.00	310000.00	VNPAY	SUCCESS	DELIVERED	Giao giờ trưa giúp mình	0	2026-08-03 10:57:00+00	2026-08-03 11:46:36+00	0.00	0.00	30000.00	24000.00
bb4e245f-83ae-4035-a2bf-7e2a411ceb6f	DH20260729-64D25	9000001004	Vũ Mai	0912456004	21.0285000	105.8542000	89 Hoàng Quốc Việt, Nghĩa Đô, Cầu Giấy, Hà Nội	21.0454358	105.7941797	4.141	150000.00	35000.00	185000.00	COD	SUCCESS	DELIVERED	\N	0	2026-07-29 11:14:00+00	2026-07-29 12:02:33.84+00	0.00	0.00	35000.00	28000.00
e8231aad-69e7-4a8e-869f-ab897492cc12	DH20260726-955A2	9000001008	Ngô Tâm	0912456008	21.0285000	105.8542000	19 Duy Tân, Dịch Vọng Hậu, Cầu Giấy, Hà Nội	21.0285190	105.7830469	1.226	40000.00	20000.00	60000.00	VNPAY	SUCCESS	DELIVERED	\N	0	2026-07-26 16:51:00+00	2026-07-26 17:34:54.24+00	0.00	0.00	20000.00	16000.00
fe4ab8a9-5c7f-4d54-9515-5f1bab09fe60	DH20260802-AFED6	9000001003	Lê Đức	0912456003	21.0285000	105.8542000	89 Hoàng Quốc Việt, Nghĩa Đô, Cầu Giấy, Hà Nội	21.0448874	105.7932417	5.070	185000.00	40000.00	225000.00	VNPAY	SUCCESS	DELIVERED	\N	0	2026-08-02 11:39:00+00	2026-08-02 12:35:16.8+00	0.00	0.00	40000.00	32000.00
e511e288-d7b5-44bf-a6fa-76f0dfca259a	DH20260806-67AD1	9000000003	Lê Chi	0901234003	21.0285000	105.8542000	191 Bà Triệu, Lê Đại Hành, Hai Bà Trưng, Hà Nội	21.0119697	105.8501417	1.530	185000.00	20000.00	205000.00	COD	SUCCESS	DELIVERED	\N	0	2026-08-06 15:08:00+00	2026-08-06 16:06:07.2+00	0.00	0.00	20000.00	16000.00
f6eb20eb-aa59-45e8-80ee-eb835617bc04	DH20260806-FEF71	9000001005	Hoàng Nam	0912456005	21.0285000	105.8542000	89 Hoàng Quốc Việt, Nghĩa Đô, Cầu Giấy, Hà Nội	21.0450888	105.7931145	5.982	165000.00	40000.00	205000.00	COD	SUCCESS	DELIVERED	\N	0	2026-08-06 16:55:00+00	2026-08-06 18:05:55.68+00	0.00	0.00	40000.00	32000.00
d107d669-b496-4dcc-ab4e-d2efb56f55cf	DH20260812-84D8D	9000001007	Bùi Huy	0912456007	21.0285000	105.8542000	78 Tây Sơn, Quang Trung, Đống Đa, Hà Nội	21.0091055	105.8228719	5.424	260000.00	40000.00	300000.00	VNPAY	SUCCESS	DELIVERED	\N	0	2026-08-12 14:08:00+00	2026-08-12 15:08:41.76+00	0.00	0.00	40000.00	32000.00
709e8b53-d695-4537-89e5-3cb2e021b8f8	DH20260802-5AAE9	9000001001	Trần Hùng	0912456001	21.0285000	105.8542000	78 Tây Sơn, Quang Trung, Đống Đa, Hà Nội	21.0087107	105.8230516	7.134	80000.00	50000.00	130000.00	COD	SUCCESS	DELIVERED	\N	0	2026-08-02 10:10:00+00	2026-08-02 11:21:32.16+00	0.00	0.00	50000.00	40000.00
7b0d565f-cfbf-451a-bba4-bb161c963675	DH20260804-D7785	9000001006	Đỗ Lan	0912456006	21.0285000	105.8542000	285 Đội Cấn, Liễu Giai, Ba Đình, Hà Nội	21.0365641	105.8218622	2.714	205000.00	25000.00	230000.00	VNPAY	SUCCESS	DELIVERED	\N	0	2026-08-04 14:14:00+00	2026-08-04 14:59:51.36+00	0.00	0.00	25000.00	20000.00
22c37396-e5be-4d5a-9c8d-f0682c1ef38e	DH20260712-E0598	9000000001	Nguyễn An	0901234001	21.0285000	105.8542000	19 Duy Tân, Dịch Vọng Hậu, Cầu Giấy, Hà Nội	21.0284305	105.7825639	7.363	285000.00	50000.00	335000.00	COD	SUCCESS	DELIVERED	\N	0	2026-07-12 13:10:00+00	2026-07-12 14:23:27.12+00	0.00	0.00	50000.00	40000.00
d5a97f80-1877-4c33-bffd-33567e09325f	DH20260810-DC68D	9000001003	Lê Đức	0912456003	21.0285000	105.8542000	15 Kim Mã, Ngọc Khánh, Ba Đình, Hà Nội	21.0315324	105.8186884	2.682	160000.00	25000.00	185000.00	COD	SUCCESS	DELIVERED	\N	0	2026-08-10 09:18:00+00	2026-08-10 09:59:43.68+00	0.00	0.00	25000.00	20000.00
7a2c6725-0fd4-4b6f-afae-4f4fd6de24c0	DH20260812-48CEE	9000001008	Ngô Tâm	0912456008	21.0285000	105.8542000	302 Cầu Giấy, Dịch Vọng, Cầu Giấy, Hà Nội	21.0330532	105.7930034	3.650	205000.00	30000.00	235000.00	COD	SUCCESS	DELIVERED	Gọi trước khi giao giúp em	0	2026-08-12 11:24:00+00	2026-08-12 12:23:36+00	0.00	0.00	30000.00	24000.00
7cb9dd02-a9d6-4f9f-98d9-1a8a8f635e35	DH20260714-04124	9000000002	Trần Bình	0901234002	21.0285000	105.8542000	120 Bạch Mai, Cầu Dền, Hai Bà Trưng, Hà Nội	21.0015412	105.8494574	4.053	180000.00	35000.00	215000.00	VNPAY	SUCCESS	DELIVERED	\N	0	2026-07-14 11:04:00+00	2026-07-14 12:01:12.72+00	0.00	0.00	35000.00	28000.00
3249dd29-eeb0-41b9-a360-66ad4c5890ea	DH20260807-B5BB7	9000001007	Bùi Huy	0912456007	21.0285000	105.8542000	15 Kim Mã, Ngọc Khánh, Ba Đình, Hà Nội	21.0310436	105.8188475	4.030	265000.00	35000.00	300000.00	COD	SUCCESS	DELIVERED	Giao giờ trưa giúp mình	0	2026-08-07 17:09:00+00	2026-08-07 18:12:07.2+00	0.00	0.00	35000.00	28000.00
18aa1028-1fb5-4643-b44e-1581931ff4e9	DH20260804-00CC7	9000001001	Trần Hùng	0912456001	21.0285000	105.8542000	120 Bạch Mai, Cầu Dền, Hai Bà Trưng, Hà Nội	21.0013846	105.8502665	6.020	110000.00	45000.00	155000.00	COD	SUCCESS	DELIVERED	\N	0	2026-08-04 12:57:00+00	2026-08-04 14:01:04.8+00	0.00	0.00	45000.00	36000.00
96d4dcd2-7a41-42b5-bbfa-2ffeda0243a1	DH20260725-471AD	9000001010	Dương Khánh	0912456010	21.0285000	105.8542000	46 Hàng Bông, Hàng Gai, Hoàn Kiếm, Hà Nội	21.0310323	105.8482792	7.917	210000.00	50000.00	260000.00	VNPAY	SUCCESS	DELIVERED	Thêm ớt	0	2026-07-25 13:17:00+00	2026-07-25 14:27:40.08+00	0.00	0.00	50000.00	40000.00
f881d853-eb35-446c-bbb3-7838c420760d	DH20260729-473E5	9000001002	Phạm Hà	0912456002	21.0285000	105.8542000	54 Nguyễn Chí Thanh, Láng Thượng, Đống Đa, Hà Nội	21.0230079	105.8081431	3.235	165000.00	30000.00	195000.00	VNPAY	SUCCESS	DELIVERED	\N	0	2026-07-29 20:59:00+00	2026-07-29 21:56:56.4+00	0.00	0.00	30000.00	24000.00
b31179de-25cd-4900-afc8-748d58e1c685	DH20260714-7AC3D	9000000003	Lê Chi	0901234003	21.0285000	105.8542000	285 Đội Cấn, Liễu Giai, Ba Đình, Hà Nội	21.0375763	105.8208889	2.128	60000.00	25000.00	85000.00	COD	SUCCESS	DELIVERED	\N	0	2026-07-14 14:13:00+00	2026-07-14 15:05:30.72+00	0.00	0.00	25000.00	20000.00
8d1f6598-0a74-4cde-955a-c81b5e646061	DH20260810-190CB	9000000003	Lê Chi	0901234003	21.0285000	105.8542000	19 Duy Tân, Dịch Vọng Hậu, Cầu Giấy, Hà Nội	21.0280822	105.7831768	2.264	305000.00	25000.00	330000.00	COD	SUCCESS	DELIVERED	Không hành	0	2026-08-10 08:22:00+00	2026-08-10 09:13:03.36+00	0.00	0.00	25000.00	20000.00
969e78cf-0b71-4f29-9fa7-bff1f6f01bdb	DH20260714-62F68	9000001010	Dương Khánh	0912456010	21.0285000	105.8542000	15 Kim Mã, Ngọc Khánh, Ba Đình, Hà Nội	21.0319880	105.8186606	3.618	140000.00	30000.00	170000.00	VNPAY	SUCCESS	DELIVERED	Không hành	0	2026-07-14 19:12:00+00	2026-07-14 20:00:28.32+00	0.00	0.00	30000.00	24000.00
3a8f8ae6-68d1-40f8-8f29-7343da049169	DH20260726-13818	9000001004	Vũ Mai	0912456004	21.0285000	105.8542000	68 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội	21.0119430	105.7964072	7.080	120000.00	50000.00	170000.00	VNPAY	SUCCESS	DELIVERED	\N	0	2026-07-26 16:26:00+00	2026-07-26 17:31:19.2+00	0.00	0.00	50000.00	40000.00
dede1587-6e75-4485-b136-abcd2f2b232c	DH20260729-6E5D1	9000001004	Vũ Mai	0912456004	21.0285000	105.8542000	68 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội	21.0117687	105.7958319	1.061	90000.00	20000.00	110000.00	COD	SUCCESS	DELIVERED	\N	0	2026-07-29 17:54:00+00	2026-07-29 18:24:14.64+00	0.00	0.00	20000.00	16000.00
641f4e85-e1f2-4ae0-a15e-7d12cae5727d	DH20260728-16484	9000000003	Lê Chi	0901234003	21.0285000	105.8542000	191 Bà Triệu, Lê Đại Hành, Hai Bà Trưng, Hà Nội	21.0118834	105.8494315	2.546	140000.00	25000.00	165000.00	COD	SUCCESS	DELIVERED	\N	0	2026-07-28 11:33:00+00	2026-07-28 12:23:11.04+00	0.00	0.00	25000.00	20000.00
7bff06a1-4b9d-435f-b0ce-f5dffadfcb91	DH20260723-04C1A	9000000003	Lê Chi	0901234003	21.0285000	105.8542000	46 Hàng Bông, Hàng Gai, Hoàn Kiếm, Hà Nội	21.0295236	105.8478411	1.732	275000.00	20000.00	295000.00	COD	SUCCESS	DELIVERED	\N	0	2026-07-23 18:06:00+00	2026-07-23 18:41:55.68+00	0.00	0.00	20000.00	16000.00
f38b2ebd-8cd5-4dd4-afbb-da238f64cd82	DH20260712-804DB	9000001001	Trần Hùng	0912456001	21.0285000	105.8542000	89 Hoàng Quốc Việt, Nghĩa Đô, Cầu Giấy, Hà Nội	21.0443912	105.7940834	7.900	50000.00	50000.00	100000.00	VNPAY	SUCCESS	DELIVERED	\N	0	2026-07-12 17:28:00+00	2026-07-12 18:50:36+00	0.00	0.00	50000.00	40000.00
33d83c92-790e-4d27-bf3e-2acab5908b0b	DH20260726-3F5F2	9000001003	Lê Đức	0912456003	21.0285000	105.8542000	191 Bà Triệu, Lê Đại Hành, Hai Bà Trưng, Hà Nội	21.0116323	105.8503893	7.713	410000.00	50000.00	460000.00	VNPAY	SUCCESS	DELIVERED	\N	0	2026-07-26 08:09:00+00	2026-07-26 09:28:51.12+00	0.00	0.00	50000.00	40000.00
2d234eb9-1a84-4b8e-acec-a2efd1c7f3ad	DH20260714-E48AD	9000000001	Nguyễn An	0901234001	21.0285000	105.8542000	33 Giảng Võ, Cát Linh, Đống Đa, Hà Nội	21.0288909	105.8295255	1.228	130000.00	20000.00	150000.00	COD	SUCCESS	DELIVERED	Gọi trước khi giao giúp em	0	2026-07-14 09:56:00+00	2026-07-14 10:39:54.72+00	0.00	0.00	20000.00	16000.00
80df0f44-9540-40ad-b325-2d62da3f93af	DH20260717-AD934	9000000001	Nguyễn An	0901234001	21.0285000	105.8542000	33 Giảng Võ, Cát Linh, Đống Đa, Hà Nội	21.0275056	105.8282919	3.324	305000.00	30000.00	335000.00	COD	SUCCESS	DELIVERED	Thêm ớt	0	2026-07-17 14:31:00+00	2026-07-17 15:24:17.76+00	0.00	0.00	30000.00	24000.00
c18f08f0-88a4-48e2-a05d-9e0026c11360	DH20260808-F3ACF	9000001009	Đặng Quỳnh	0912456009	21.0285000	105.8542000	46 Hàng Bông, Hàng Gai, Hoàn Kiếm, Hà Nội	21.0306669	105.8478575	5.889	120000.00	40000.00	160000.00	VNPAY	PENDING	CANCELLED	\N	0	2026-08-08 09:20:00+00	2026-08-08 09:46:00+00	0.00	0.00	40000.00	\N
761362f6-9711-471b-8fbc-c897ef2d0c02	DH20260713-48CE9	9000001009	Đặng Quỳnh	0912456009	21.0285000	105.8542000	120 Bạch Mai, Cầu Dền, Hai Bà Trưng, Hà Nội	21.0009484	105.8505448	5.697	290000.00	40000.00	330000.00	COD	SUCCESS	DELIVERED	\N	0	2026-07-13 18:27:00+00	2026-07-13 19:32:47.28+00	0.00	0.00	40000.00	32000.00
534f8b1a-1248-4d07-a315-0f7781edc41b	DH20260724-9B7BE	9000001005	Hoàng Nam	0912456005	21.0285000	105.8542000	33 Giảng Võ, Cát Linh, Đống Đa, Hà Nội	21.0279509	105.8287371	6.125	80000.00	45000.00	125000.00	COD	SUCCESS	DELIVERED	Để ở sảnh chung cư, gọi em xuống lấy	0	2026-07-24 14:08:00+00	2026-07-24 15:10:30+00	0.00	0.00	45000.00	36000.00
b3868885-f520-46ba-93fc-22ee350d22d0	DH20260724-44BAA	9000001002	Phạm Hà	0912456002	21.0285000	105.8542000	210 Nguyễn Trãi, Thượng Đình, Thanh Xuân, Hà Nội	20.9940270	105.8121488	4.022	25000.00	35000.00	60000.00	COD	SUCCESS	DELIVERED	\N	0	2026-07-24 11:15:00+00	2026-07-24 12:12:05.28+00	0.00	0.00	35000.00	28000.00
0010521a-9d1e-4b44-9eb8-e450dc153c52	DH20260722-F9BCD	9000001007	Bùi Huy	0912456007	21.0285000	105.8542000	68 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội	21.0116292	105.7962116	4.503	110000.00	35000.00	145000.00	COD	SUCCESS	DELIVERED	\N	0	2026-07-22 18:33:00+00	2026-07-22 19:20:00.72+00	0.00	0.00	35000.00	28000.00
798a9bb7-edac-4b3c-b7d8-a4e7b9f4bcba	DH20260813-C5C7A	9000001007	Bùi Huy	0912456007	21.0285000	105.8542000	33 Giảng Võ, Cát Linh, Đống Đa, Hà Nội	21.0274595	105.8278664	4.853	235000.00	35000.00	270000.00	COD	PENDING	CONFIRMED	\N	0	2026-08-13 06:43:35.719639+00	2026-08-13 06:52:35.719639+00	0.00	0.00	35000.00	\N
ca4b3cad-5556-493f-9e9d-42d73d6a0f01	DH20260813-31964	9000001003	Lê Đức	0912456003	21.0285000	105.8542000	120 Bạch Mai, Cầu Dền, Hai Bà Trưng, Hà Nội	21.0013339	105.8512070	7.335	240000.00	50000.00	290000.00	COD	PENDING	CONFIRMED	\N	0	2026-08-13 06:43:35.719639+00	2026-08-13 06:50:35.719639+00	0.00	0.00	50000.00	\N
261c9030-2493-4cd2-bf6d-bd69fccca4f7	DH20260813-83E1A	9000000001	Nguyễn An	0901234001	21.0285000	105.8542000	15 Kim Mã, Ngọc Khánh, Ba Đình, Hà Nội	21.0314546	105.8194006	1.028	120000.00	20000.00	140000.00	VNPAY	SUCCESS	PENDING	\N	0	2026-08-13 05:43:35.719639+00	2026-08-13 05:43:35.719639+00	0.00	0.00	20000.00	\N
4ca4e8e4-fd31-45f5-8828-fdf505c3ffd4	DH20260813-D699F	9000001010	Dương Khánh	0912456010	21.0285000	105.8542000	285 Đội Cấn, Liễu Giai, Ba Đình, Hà Nội	21.0368198	105.8201563	7.355	25000.00	50000.00	75000.00	COD	SUCCESS	DELIVERED	\N	0	2026-08-13 05:43:35.719639+00	2026-08-13 06:59:00.919639+00	0.00	0.00	50000.00	40000.00
\.


--
-- Data for Name: payment; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.payment (id, order_id, method, amount, status, vnp_txn_ref, vnp_transaction_no, vnp_response_code, paid_at, version, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: payment_transaction; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.payment_transaction (id, payment_id, event_type, raw_payload, recorded_at) FROM stdin;
\.


--
-- Data for Name: processed_update; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.processed_update (update_id, processed_at) FROM stdin;
\.


--
-- Data for Name: product; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.product (id, name, description, price, image_url, stock, is_active, created_at, updated_at, category) FROM stdin;
1	Phở bò tái	Phở bò truyền thống, nước dùng đậm đà	55000.00	https://upload.wikimedia.org/wikipedia/commons/thumb/9/96/Pho-Beef-Noodle-Soup-2008.jpg/960px-Pho-Beef-Noodle-Soup-2008.jpg	100	t	2026-08-13 07:43:35.448938+00	2026-08-13 07:43:35.448938+00	food
2	Bún chả Hà Nội	Bún chả nướng than hoa, kèm rau sống	60000.00	https://upload.wikimedia.org/wikipedia/commons/thumb/7/7f/Bun-cha-hanoi.jpg/960px-Bun-cha-hanoi.jpg	80	t	2026-08-13 07:43:35.448938+00	2026-08-13 07:43:35.448938+00	food
3	Bánh mì pate	Bánh mì pate, dưa leo, rau thơm	25000.00	https://upload.wikimedia.org/wikipedia/commons/thumb/5/5a/B%C3%A1nh_mi_Sandwich%28Takadanobaba%29IMG_20220215_104413_02.jpg/960px-B%C3%A1nh_mi_Sandwich%28Takadanobaba%29IMG_20220215_104413_02.jpg	150	t	2026-08-13 07:43:35.448938+00	2026-08-13 07:43:35.448938+00	food
4	Cơm gà xối mỡ	Cơm gà giòn, sốt mắm tỏi	65000.00	https://upload.wikimedia.org/wikipedia/commons/thumb/0/0b/C%C6%A1m_g%C3%A0_chi%C3%AAn_%E1%BB%9F_%C4%90%C3%B4ng_H%C3%A0_n%C4%83m_2017_%282%29.jpg/960px-C%C6%A1m_g%C3%A0_chi%C3%AAn_%E1%BB%9F_%C4%90%C3%B4ng_H%C3%A0_n%C4%83m_2017_%282%29.jpg	90	t	2026-08-13 07:43:35.448938+00	2026-08-13 07:43:35.448938+00	food
5	Bún bò Huế	Bún bò Huế cay nồng, giò heo	70000.00	https://upload.wikimedia.org/wikipedia/commons/thumb/0/00/Bun-Bo-Hue-from-Huong-Giang-2011.jpg/960px-Bun-Bo-Hue-from-Huong-Giang-2011.jpg	60	t	2026-08-13 07:43:35.448938+00	2026-08-13 07:43:35.448938+00	food
6	Trà sữa trân châu	Trà sữa size L, trân châu đường đen	35000.00	https://upload.wikimedia.org/wikipedia/commons/thumb/3/3d/Pearl_Milk_Tea_in_Chun_Shui_Tang_%28cropped%29.jpg/960px-Pearl_Milk_Tea_in_Chun_Shui_Tang_%28cropped%29.jpg	200	t	2026-08-13 07:43:35.448938+00	2026-08-13 07:43:35.448938+00	drink
7	Cà phê sữa đá	Cà phê phin, sữa đặc, đá	20000.00	https://upload.wikimedia.org/wikipedia/commons/thumb/b/bb/Vietnamese_iced_coffee_-_Jan_31%2C_2018.jpg/960px-Vietnamese_iced_coffee_-_Jan_31%2C_2018.jpg	200	t	2026-08-13 07:43:35.448938+00	2026-08-13 07:43:35.448938+00	drink
8	Nem rán Hà Nội	Nem rán giòn, kèm nước chấm chua ngọt	45000.00	https://upload.wikimedia.org/wikipedia/commons/thumb/5/51/Vietnamese_fried_spring_rolls_in_Ho_Chi_Minh_City%2C_Vietnam.jpg/960px-Vietnamese_fried_spring_rolls_in_Ho_Chi_Minh_City%2C_Vietnam.jpg	120	t	2026-08-13 07:43:35.448938+00	2026-08-13 07:43:35.448938+00	food
9	Chè bưởi	Chè bưởi mát lạnh, nước cốt dừa	30000.00	https://upload.wikimedia.org/wikipedia/commons/thumb/c/c3/Ch%C3%A8_b%C3%A0_ba.jpg/960px-Ch%C3%A8_b%C3%A0_ba.jpg	70	t	2026-08-13 07:43:35.448938+00	2026-08-13 07:43:35.448938+00	dessert
10	Bánh xèo miền Tây	Bánh xèo giòn, tôm thịt, rau sống	50000.00	https://upload.wikimedia.org/wikipedia/commons/thumb/e/e5/B%C3%A1nh_x%C3%A8o_1.jpg/960px-B%C3%A1nh_x%C3%A8o_1.jpg	50	t	2026-08-13 07:43:35.448938+00	2026-08-13 07:43:35.448938+00	food
11	Bún riêu cua	Bún riêu cua đồng, đậu rán, giấm bỗng	50000.00	https://upload.wikimedia.org/wikipedia/commons/thumb/f/f0/B%C3%BAn_ri%C3%AAu_cua%2C_crab_roe_pate_and_fried_shallots.jpg/960px-B%C3%BAn_ri%C3%AAu_cua%2C_crab_roe_pate_and_fried_shallots.jpg	80	t	2026-08-13 07:43:35.701898+00	2026-08-13 07:43:35.701898+00	food
12	Bánh cuốn Thanh Trì	Bánh cuốn nóng, chả quế, hành phi	40000.00	https://upload.wikimedia.org/wikipedia/commons/thumb/b/b2/B%C3%A1nh_cu%E1%BB%91n_Thanh_Tr%C3%AC.jpg/960px-B%C3%A1nh_cu%E1%BB%91n_Thanh_Tr%C3%AC.jpg	90	t	2026-08-13 07:43:35.701898+00	2026-08-13 07:43:35.701898+00	food
13	Xôi gà	Xôi nếp dẻo, gà xé, lạp xưởng, trứng non	45000.00	https://upload.wikimedia.org/wikipedia/commons/thumb/7/7c/X%C3%B4i_G%C3%A0_Tr%E1%BB%A9ng_Non_L%E1%BA%A1p_X%C6%B0%E1%BB%9Bng_%28Sticky_rice_with_chicken_and_eggs%2C_and_chinese_sausages%29.jpg/960px-X%C3%B4i_G%C3%A0_Tr%E1%BB%A9ng_Non_L%E1%BA%A1p_X%C6%B0%E1%BB%9Bng_%28Sticky_rice_with_chicken_and_eggs%2C_and_chinese_sausages%29.jpg	70	t	2026-08-13 07:43:35.701898+00	2026-08-13 07:43:35.701898+00	food
14	Gỏi cuốn tôm thịt	Gỏi cuốn tươi, chấm tương đậu phộng (5 cuốn)	40000.00	https://upload.wikimedia.org/wikipedia/commons/thumb/b/b2/Summer_rolls_with_peanut_sauce.jpg/960px-Summer_rolls_with_peanut_sauce.jpg	100	t	2026-08-13 07:43:35.701898+00	2026-08-13 07:43:35.701898+00	food
15	Cơm tấm sườn nướng	Cơm tấm, sườn nướng mật ong, trứng ốp la	60000.00	https://upload.wikimedia.org/wikipedia/commons/thumb/8/8e/Com-Tam-2008.jpg/960px-Com-Tam-2008.jpg	85	t	2026-08-13 07:43:35.701898+00	2026-08-13 07:43:35.701898+00	food
16	Nước mía	Nước mía ép nguyên chất, thêm tắc	15000.00	https://upload.wikimedia.org/wikipedia/commons/6/63/Sugarcanejuice.jpg	200	t	2026-08-13 07:43:35.701898+00	2026-08-13 07:43:35.701898+00	drink
17	Sinh tố bơ	Sinh tố bơ sáp Đắk Lắk, sữa đặc	40000.00	https://upload.wikimedia.org/wikipedia/commons/thumb/a/a5/Sinh_t%E1%BB%91_b%C6%A1.jpg/960px-Sinh_t%E1%BB%91_b%C6%A1.jpg	120	t	2026-08-13 07:43:35.701898+00	2026-08-13 07:43:35.701898+00	drink
18	Nước cam ép	Cam sành vắt nguyên chất, không đường	30000.00	https://upload.wikimedia.org/wikipedia/commons/thumb/6/67/Orange_juice_1_edit1.jpg/960px-Orange_juice_1_edit1.jpg	150	t	2026-08-13 07:43:35.701898+00	2026-08-13 07:43:35.701898+00	drink
19	Chè thập cẩm	Chè đậu đỏ, đậu xanh, thạch, nước cốt dừa	30000.00	https://upload.wikimedia.org/wikipedia/commons/thumb/7/75/Chendol2.jpg/960px-Chendol2.jpg	90	t	2026-08-13 07:43:35.701898+00	2026-08-13 07:43:35.701898+00	dessert
20	Bánh flan	Bánh flan trứng sữa, caramel đắng nhẹ (2 hộp)	25000.00	https://upload.wikimedia.org/wikipedia/commons/thumb/6/64/Cr%C3%A8me_caramel_2.jpg/960px-Cr%C3%A8me_caramel_2.jpg	110	t	2026-08-13 07:43:35.701898+00	2026-08-13 07:43:35.701898+00	dessert
\.


--
-- Data for Name: rating; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.rating (id, order_id, customer_id, shipper_id, stars, comment, created_at) FROM stdin;
11	ef7351ae-470c-4e0a-b7b5-38c30103954e	9000001008	9000000104	4	\N	2026-08-03 19:43:31.44+00
12	95cd2744-9d5a-4846-a675-8eff8f3a0e23	9000001007	9000000105	5	Giao hơi trễ nhưng shipper có gọi báo trước	2026-08-09 19:01:50.4+00
13	07a155a6-f0b2-44f6-a4ad-82d05d35d3ec	9000000002	9000000104	5	Lần sau sẽ đặt nữa	2026-08-09 17:17:19.68+00
14	ccce1074-9c82-45c9-b742-a13a4aa32819	9000001009	9000000102	5	Đồ ăn ngon, sẽ ủng hộ tiếp	2026-08-07 18:10:46.8+00
15	e522f7bf-5a5b-4403-9534-a7c52c639537	9000001006	9000000104	5	Shipper thân thiện, đúng giờ	2026-07-26 21:40:38.4+00
16	65d924e2-e7a9-4eb9-b2b1-9782031b146f	9000001006	9000000101	4	Lần sau sẽ đặt nữa	2026-07-22 11:53:02.4+00
17	4424346a-3dd9-4084-8fb6-20fc52b977ed	9000001008	9000000104	5	Món ngon, giao đúng giờ	2026-07-26 19:04:24.24+00
18	ce9f4b64-25f0-4bf6-86d6-8bdb6af5f1f6	9000001003	9000000102	2	Giao nhanh, món còn nóng!	2026-07-14 20:03:45.12+00
19	1371d53b-f0ec-49fc-ac00-64e5ddc7d66a	9000001003	9000000105	5	Đóng gói cẩn thận	2026-07-28 18:04:49.44+00
20	927ca6be-5d68-4abf-be79-c4b847b68df9	9000001009	9000000102	5	Shipper thân thiện, đúng giờ	2026-08-08 18:13:53.28+00
21	c0aa3bb4-9c7e-4795-9e47-81328313713b	9000001003	9000000104	5	Đồ ăn ngon, sẽ ủng hộ tiếp	2026-07-12 11:48:06.48+00
22	09f4f7ac-58af-4dee-885a-12fda8414336	9000001009	9000000105	5	Đồ ăn ngon, sẽ ủng hộ tiếp	2026-08-01 10:19:11.04+00
23	7ddabb80-7b7a-4a4d-9c58-c0203c31fc46	9000001007	9000000104	4	\N	2026-07-24 12:54:18.48+00
24	54e966e2-4009-4bf8-ad7f-68413dce74a1	9000000001	9000000102	5	Lần sau sẽ đặt nữa	2026-07-28 10:12:52.32+00
25	e3864da8-2daf-4740-b0d7-095804a16de2	9000000001	9000000102	5	\N	2026-08-07 18:04:06+00
26	7cc0b027-15f7-4145-90cf-835ec0553793	9000001004	9000000101	5	Giao hơi trễ nhưng shipper có gọi báo trước	2026-08-03 13:36:27.36+00
27	9cbcb6d1-8dbf-454a-9bc0-8f236b81c735	9000001007	9000000102	5	Đóng gói cẩn thận	2026-07-24 20:11:33.12+00
28	12858304-2565-4efa-ac44-967e3754d616	9000001006	9000000102	5	Giao nhanh, món còn nóng!	2026-07-28 22:49:24.24+00
29	1aa7c754-bb0e-4e83-b947-9c17aeb65e53	9000001005	9000000105	5	Giao hơi trễ nhưng shipper có gọi báo trước	2026-08-04 16:57:23.04+00
30	49b65a2e-19da-49a0-8964-cfc8ac32e005	9000001004	9000000104	5	\N	2026-07-14 13:42:06+00
31	bf09be0c-bf44-45e6-91de-7505423d777c	9000001001	9000000101	5	\N	2026-07-13 22:19:40.32+00
32	daf837c8-14c0-4eca-99a4-3fd9c9a3fa5c	9000001006	9000000104	5	Lần sau sẽ đặt nữa	2026-08-03 12:34:36+00
33	e8231aad-69e7-4a8e-869f-ab897492cc12	9000001008	9000000104	5	\N	2026-07-26 18:41:54.24+00
34	e511e288-d7b5-44bf-a6fa-76f0dfca259a	9000000003	9000000105	3	Shipper thân thiện, đúng giờ	2026-08-06 17:30:07.2+00
35	f6eb20eb-aa59-45e8-80ee-eb835617bc04	9000001005	9000000104	4	Giao hơi trễ nhưng shipper có gọi báo trước	2026-08-06 19:17:55.68+00
36	709e8b53-d695-4537-89e5-3cb2e021b8f8	9000001001	9000000105	4	Lần sau sẽ đặt nữa	2026-08-02 11:31:32.16+00
37	7b0d565f-cfbf-451a-bba4-bb161c963675	9000001006	9000000104	2	\N	2026-08-04 15:06:51.36+00
38	d5a97f80-1877-4c33-bffd-33567e09325f	9000001003	9000000105	4	Đóng gói cẩn thận	2026-08-10 11:04:43.68+00
39	3249dd29-eeb0-41b9-a360-66ad4c5890ea	9000001007	9000000104	5	\N	2026-08-07 19:59:07.2+00
40	18aa1028-1fb5-4643-b44e-1581931ff4e9	9000001001	9000000105	4	Giao nhanh, món còn nóng!	2026-08-04 14:42:04.8+00
41	96d4dcd2-7a41-42b5-bbfa-2ffeda0243a1	9000001010	9000000101	4	Giao hơi trễ nhưng shipper có gọi báo trước	2026-07-25 15:15:40.08+00
42	f881d853-eb35-446c-bbb3-7838c420760d	9000001002	9000000104	5	Shipper thân thiện, đúng giờ	2026-07-29 23:17:56.4+00
43	b31179de-25cd-4900-afc8-748d58e1c685	9000000003	9000000102	5	\N	2026-07-14 17:05:30.72+00
44	dede1587-6e75-4485-b136-abcd2f2b232c	9000001004	9000000105	4	Lần sau sẽ đặt nữa	2026-07-29 18:29:14.64+00
45	641f4e85-e1f2-4ae0-a15e-7d12cae5727d	9000000003	9000000105	5	Giao nhanh, món còn nóng!	2026-07-28 13:38:11.04+00
46	7bff06a1-4b9d-435f-b0ce-f5dffadfcb91	9000000003	9000000101	5	Đồ ăn ngon, sẽ ủng hộ tiếp	2026-07-23 19:21:55.68+00
47	f38b2ebd-8cd5-4dd4-afbb-da238f64cd82	9000001001	9000000105	5	\N	2026-07-12 19:31:36+00
48	33d83c92-790e-4d27-bf3e-2acab5908b0b	9000001003	9000000105	5	Giao nhanh, món còn nóng!	2026-07-26 10:32:51.12+00
49	2d234eb9-1a84-4b8e-acec-a2efd1c7f3ad	9000000001	9000000102	5	\N	2026-07-14 11:23:54.72+00
50	80df0f44-9540-40ad-b325-2d62da3f93af	9000000001	9000000101	2	Rất hài lòng	2026-07-17 15:36:17.76+00
51	534f8b1a-1248-4d07-a315-0f7781edc41b	9000001005	9000000101	2	Lần sau sẽ đặt nữa	2026-07-24 17:03:30+00
52	b3868885-f520-46ba-93fc-22ee350d22d0	9000001002	9000000101	4	\N	2026-07-24 12:23:05.28+00
53	0010521a-9d1e-4b44-9eb8-e450dc153c52	9000001007	9000000102	5	Lần sau sẽ đặt nữa	2026-07-22 19:47:00.72+00
54	4ca4e8e4-fd31-45f5-8828-fdf505c3ffd4	9000001010	9000000105	5	\N	2026-08-13 07:26:00.919639+00
\.


--
-- Data for Name: refresh_token; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.refresh_token (id, admin_user_id, token_hash, expires_at, revoked, created_at) FROM stdin;
\.


--
-- Data for Name: saved_address; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.saved_address (id, customer_id, address, lat, lng, use_count, last_used_at, created_at) FROM stdin;
1	9000001001	68 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội	21.0119000	105.7965000	4	2026-08-11 07:43:35.713634+00	2026-08-13 07:43:35.713634+00
2	9000001002	25 Láng Hạ, Thành Công, Ba Đình, Hà Nội	21.0170000	105.8135000	6	2026-08-12 07:43:35.713634+00	2026-08-13 07:43:35.713634+00
3	9000001003	191 Bà Triệu, Lê Đại Hành, Hai Bà Trưng, Hà Nội	21.0125000	105.8494000	2	2026-08-08 07:43:35.713634+00	2026-08-13 07:43:35.713634+00
4	9000001004	54 Nguyễn Chí Thanh, Láng Thượng, Đống Đa, Hà Nội	21.0227000	105.8085000	3	2026-08-10 07:43:35.713634+00	2026-08-13 07:43:35.713634+00
5	9000001005	89 Hoàng Quốc Việt, Nghĩa Đô, Cầu Giấy, Hà Nội	21.0453000	105.7936000	5	2026-08-12 19:43:35.713634+00	2026-08-13 07:43:35.713634+00
\.


--
-- Data for Name: shipper_ledger; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.shipper_ledger (id, shipper_id, entry_type, amount, order_id, note, created_by, created_at) FROM stdin;
1	9000000104	COMMISSION	28000.00	ef7351ae-470c-4e0a-b7b5-38c30103954e	Hoa hồng đơn DH20260803-FB399	system	2026-08-03 19:17:31.44+00
2	9000000104	COD_OWED	-155000.00	ef7351ae-470c-4e0a-b7b5-38c30103954e	Shipper đã thu COD đơn DH20260803-FB399	system	2026-08-03 19:17:31.44+00
3	9000000102	COMMISSION	28000.00	cf485f7a-c0d8-4e2f-b684-ae10bb5c10d5	Hoa hồng đơn DH20260723-691ED	system	2026-07-23 21:00:50.4+00
4	9000000102	COD_OWED	-165000.00	cf485f7a-c0d8-4e2f-b684-ae10bb5c10d5	Shipper đã thu COD đơn DH20260723-691ED	system	2026-07-23 21:00:50.4+00
5	9000000105	COMMISSION	24000.00	95cd2744-9d5a-4846-a675-8eff8f3a0e23	Hoa hồng đơn DH20260809-C6460	system	2026-08-09 18:29:50.4+00
6	9000000105	COD_OWED	-130000.00	95cd2744-9d5a-4846-a675-8eff8f3a0e23	Shipper đã thu COD đơn DH20260809-C6460	system	2026-08-09 18:29:50.4+00
7	9000000102	COMMISSION	32000.00	e5d75285-641a-404c-b22d-ed3ce396da96	Hoa hồng đơn DH20260717-40BC6	system	2026-07-17 13:45:54+00
8	9000000104	COMMISSION	40000.00	07a155a6-f0b2-44f6-a4ad-82d05d35d3ec	Hoa hồng đơn DH20260809-042C2	system	2026-08-09 15:30:19.68+00
9	9000000104	COD_OWED	-275000.00	07a155a6-f0b2-44f6-a4ad-82d05d35d3ec	Shipper đã thu COD đơn DH20260809-042C2	system	2026-08-09 15:30:19.68+00
10	9000000102	COMMISSION	28000.00	ccce1074-9c82-45c9-b742-a13a4aa32819	Hoa hồng đơn DH20260807-FECA4	system	2026-08-07 17:11:46.8+00
11	9000000102	COD_OWED	-180000.00	ccce1074-9c82-45c9-b742-a13a4aa32819	Shipper đã thu COD đơn DH20260807-FECA4	system	2026-08-07 17:11:46.8+00
12	9000000104	COMMISSION	16000.00	e522f7bf-5a5b-4403-9534-a7c52c639537	Hoa hồng đơn DH20260726-C8988	system	2026-07-26 21:05:38.4+00
13	9000000102	COMMISSION	16000.00	58b1c5da-2487-4332-a0f5-0406d45f6fb6	Hoa hồng đơn DH20260804-73729	system	2026-08-04 09:14:37.44+00
14	9000000102	COD_OWED	-230000.00	58b1c5da-2487-4332-a0f5-0406d45f6fb6	Shipper đã thu COD đơn DH20260804-73729	system	2026-08-04 09:14:37.44+00
15	9000000101	COMMISSION	40000.00	65d924e2-e7a9-4eb9-b2b1-9782031b146f	Hoa hồng đơn DH20260722-C7443	system	2026-07-22 11:41:02.4+00
16	9000000104	COMMISSION	36000.00	4424346a-3dd9-4084-8fb6-20fc52b977ed	Hoa hồng đơn DH20260726-C891B	system	2026-07-26 18:48:24.24+00
17	9000000104	COD_OWED	-210000.00	4424346a-3dd9-4084-8fb6-20fc52b977ed	Shipper đã thu COD đơn DH20260726-C891B	system	2026-07-26 18:48:24.24+00
18	9000000102	COMMISSION	20000.00	ce9f4b64-25f0-4bf6-86d6-8bdb6af5f1f6	Hoa hồng đơn DH20260714-57455	system	2026-07-14 19:55:45.12+00
19	9000000102	COD_OWED	-190000.00	ce9f4b64-25f0-4bf6-86d6-8bdb6af5f1f6	Shipper đã thu COD đơn DH20260714-57455	system	2026-07-14 19:55:45.12+00
20	9000000105	COMMISSION	40000.00	1371d53b-f0ec-49fc-ac00-64e5ddc7d66a	Hoa hồng đơn DH20260728-BCD65	system	2026-07-28 17:33:49.44+00
21	9000000104	COMMISSION	36000.00	f539a609-fa6a-4e2b-85eb-6b1d8ed9e2fd	Hoa hồng đơn DH20260719-3F4B6	system	2026-07-19 21:52:05.52+00
22	9000000102	COMMISSION	40000.00	927ca6be-5d68-4abf-be79-c4b847b68df9	Hoa hồng đơn DH20260808-F69B6	system	2026-08-08 17:13:53.28+00
23	9000000102	COD_OWED	-65000.00	927ca6be-5d68-4abf-be79-c4b847b68df9	Shipper đã thu COD đơn DH20260808-F69B6	system	2026-08-08 17:13:53.28+00
24	9000000104	COMMISSION	32000.00	c0aa3bb4-9c7e-4795-9e47-81328313713b	Hoa hồng đơn DH20260712-25207	system	2026-07-12 10:47:06.48+00
25	9000000105	COMMISSION	32000.00	09f4f7ac-58af-4dee-885a-12fda8414336	Hoa hồng đơn DH20260801-6B2DB	system	2026-08-01 09:50:11.04+00
26	9000000104	COMMISSION	28000.00	7ddabb80-7b7a-4a4d-9c58-c0203c31fc46	Hoa hồng đơn DH20260724-E8662	system	2026-07-24 11:29:18.48+00
27	9000000104	COD_OWED	-265000.00	7ddabb80-7b7a-4a4d-9c58-c0203c31fc46	Shipper đã thu COD đơn DH20260724-E8662	system	2026-07-24 11:29:18.48+00
28	9000000102	COMMISSION	16000.00	54e966e2-4009-4bf8-ad7f-68413dce74a1	Hoa hồng đơn DH20260728-33599	system	2026-07-28 08:51:52.32+00
29	9000000102	COD_OWED	-290000.00	54e966e2-4009-4bf8-ad7f-68413dce74a1	Shipper đã thu COD đơn DH20260728-33599	system	2026-07-28 08:51:52.32+00
30	9000000101	COMMISSION	24000.00	9d94bef9-0883-414b-8af1-279b195670fc	Hoa hồng đơn DH20260809-4CFEA	system	2026-08-09 16:26:24.72+00
31	9000000102	COMMISSION	20000.00	e3864da8-2daf-4740-b0d7-095804a16de2	Hoa hồng đơn DH20260807-82F91	system	2026-08-07 16:54:06+00
32	9000000102	COD_OWED	-195000.00	e3864da8-2daf-4740-b0d7-095804a16de2	Shipper đã thu COD đơn DH20260807-82F91	system	2026-08-07 16:54:06+00
33	9000000101	COMMISSION	36000.00	7cc0b027-15f7-4145-90cf-835ec0553793	Hoa hồng đơn DH20260803-60E29	system	2026-08-03 11:43:27.36+00
34	9000000101	COD_OWED	-275000.00	7cc0b027-15f7-4145-90cf-835ec0553793	Shipper đã thu COD đơn DH20260803-60E29	system	2026-08-03 11:43:27.36+00
35	9000000102	COMMISSION	36000.00	9cbcb6d1-8dbf-454a-9bc0-8f236b81c735	Hoa hồng đơn DH20260724-0E6D0	system	2026-07-24 18:52:33.12+00
36	9000000102	COD_OWED	-95000.00	9cbcb6d1-8dbf-454a-9bc0-8f236b81c735	Shipper đã thu COD đơn DH20260724-0E6D0	system	2026-07-24 18:52:33.12+00
37	9000000102	COMMISSION	20000.00	12858304-2565-4efa-ac44-967e3754d616	Hoa hồng đơn DH20260728-72A48	system	2026-07-28 21:16:24.24+00
38	9000000102	COD_OWED	-130000.00	12858304-2565-4efa-ac44-967e3754d616	Shipper đã thu COD đơn DH20260728-72A48	system	2026-07-28 21:16:24.24+00
39	9000000101	COMMISSION	20000.00	ba3e4076-c563-42b7-ba2b-4bfa199b57df	Hoa hồng đơn DH20260718-0C407	system	2026-07-18 18:06:14.64+00
40	9000000102	COMMISSION	32000.00	156d9c3b-a75a-4ffa-9c0c-65d5620cdcbc	Hoa hồng đơn DH20260717-14BA6	system	2026-07-17 12:07:17.76+00
41	9000000102	COD_OWED	-370000.00	156d9c3b-a75a-4ffa-9c0c-65d5620cdcbc	Shipper đã thu COD đơn DH20260717-14BA6	system	2026-07-17 12:07:17.76+00
42	9000000102	COMMISSION	40000.00	feed5ba3-8777-4c41-abb3-d6379437e71c	Hoa hồng đơn DH20260805-296FE	system	2026-08-05 20:26:28.32+00
43	9000000102	COD_OWED	-125000.00	feed5ba3-8777-4c41-abb3-d6379437e71c	Shipper đã thu COD đơn DH20260805-296FE	system	2026-08-05 20:26:28.32+00
44	9000000105	COMMISSION	16000.00	1aa7c754-bb0e-4e83-b947-9c17aeb65e53	Hoa hồng đơn DH20260804-B8BAC	system	2026-08-04 16:49:23.04+00
45	9000000105	COD_OWED	-135000.00	1aa7c754-bb0e-4e83-b947-9c17aeb65e53	Shipper đã thu COD đơn DH20260804-B8BAC	system	2026-08-04 16:49:23.04+00
46	9000000105	COMMISSION	24000.00	41c5b85b-1961-499c-9ec6-2a61eb71865f	Hoa hồng đơn DH20260731-705F5	system	2026-07-31 09:42:23.76+00
47	9000000105	COD_OWED	-155000.00	41c5b85b-1961-499c-9ec6-2a61eb71865f	Shipper đã thu COD đơn DH20260731-705F5	system	2026-07-31 09:42:23.76+00
48	9000000102	COMMISSION	36000.00	d5dbe7df-d262-49ef-b18f-13938b290901	Hoa hồng đơn DH20260805-B4880	system	2026-08-05 10:56:47.04+00
49	9000000104	COMMISSION	40000.00	49b65a2e-19da-49a0-8964-cfc8ac32e005	Hoa hồng đơn DH20260714-50EF4	system	2026-07-14 13:36:06+00
50	9000000101	COMMISSION	40000.00	bf09be0c-bf44-45e6-91de-7505423d777c	Hoa hồng đơn DH20260713-A4C24	system	2026-07-13 20:47:40.32+00
51	9000000104	COMMISSION	24000.00	daf837c8-14c0-4eca-99a4-3fd9c9a3fa5c	Hoa hồng đơn DH20260803-E992E	system	2026-08-03 11:46:36+00
52	9000000102	COMMISSION	28000.00	bb4e245f-83ae-4035-a2bf-7e2a411ceb6f	Hoa hồng đơn DH20260729-64D25	system	2026-07-29 12:02:33.84+00
53	9000000102	COD_OWED	-185000.00	bb4e245f-83ae-4035-a2bf-7e2a411ceb6f	Shipper đã thu COD đơn DH20260729-64D25	system	2026-07-29 12:02:33.84+00
54	9000000104	COMMISSION	16000.00	e8231aad-69e7-4a8e-869f-ab897492cc12	Hoa hồng đơn DH20260726-955A2	system	2026-07-26 17:34:54.24+00
55	9000000104	COMMISSION	32000.00	fe4ab8a9-5c7f-4d54-9515-5f1bab09fe60	Hoa hồng đơn DH20260802-AFED6	system	2026-08-02 12:35:16.8+00
56	9000000105	COMMISSION	16000.00	e511e288-d7b5-44bf-a6fa-76f0dfca259a	Hoa hồng đơn DH20260806-67AD1	system	2026-08-06 16:06:07.2+00
57	9000000105	COD_OWED	-205000.00	e511e288-d7b5-44bf-a6fa-76f0dfca259a	Shipper đã thu COD đơn DH20260806-67AD1	system	2026-08-06 16:06:07.2+00
58	9000000104	COMMISSION	32000.00	f6eb20eb-aa59-45e8-80ee-eb835617bc04	Hoa hồng đơn DH20260806-FEF71	system	2026-08-06 18:05:55.68+00
59	9000000104	COD_OWED	-205000.00	f6eb20eb-aa59-45e8-80ee-eb835617bc04	Shipper đã thu COD đơn DH20260806-FEF71	system	2026-08-06 18:05:55.68+00
60	9000000101	COMMISSION	32000.00	d107d669-b496-4dcc-ab4e-d2efb56f55cf	Hoa hồng đơn DH20260812-84D8D	system	2026-08-12 15:08:41.76+00
61	9000000105	COMMISSION	40000.00	709e8b53-d695-4537-89e5-3cb2e021b8f8	Hoa hồng đơn DH20260802-5AAE9	system	2026-08-02 11:21:32.16+00
62	9000000105	COD_OWED	-130000.00	709e8b53-d695-4537-89e5-3cb2e021b8f8	Shipper đã thu COD đơn DH20260802-5AAE9	system	2026-08-02 11:21:32.16+00
63	9000000104	COMMISSION	20000.00	7b0d565f-cfbf-451a-bba4-bb161c963675	Hoa hồng đơn DH20260804-D7785	system	2026-08-04 14:59:51.36+00
64	9000000102	COMMISSION	40000.00	22c37396-e5be-4d5a-9c8d-f0682c1ef38e	Hoa hồng đơn DH20260712-E0598	system	2026-07-12 14:23:27.12+00
65	9000000102	COD_OWED	-335000.00	22c37396-e5be-4d5a-9c8d-f0682c1ef38e	Shipper đã thu COD đơn DH20260712-E0598	system	2026-07-12 14:23:27.12+00
66	9000000105	COMMISSION	20000.00	d5a97f80-1877-4c33-bffd-33567e09325f	Hoa hồng đơn DH20260810-DC68D	system	2026-08-10 09:59:43.68+00
67	9000000105	COD_OWED	-185000.00	d5a97f80-1877-4c33-bffd-33567e09325f	Shipper đã thu COD đơn DH20260810-DC68D	system	2026-08-10 09:59:43.68+00
68	9000000101	COMMISSION	24000.00	7a2c6725-0fd4-4b6f-afae-4f4fd6de24c0	Hoa hồng đơn DH20260812-48CEE	system	2026-08-12 12:23:36+00
69	9000000101	COD_OWED	-235000.00	7a2c6725-0fd4-4b6f-afae-4f4fd6de24c0	Shipper đã thu COD đơn DH20260812-48CEE	system	2026-08-12 12:23:36+00
70	9000000102	COMMISSION	28000.00	7cb9dd02-a9d6-4f9f-98d9-1a8a8f635e35	Hoa hồng đơn DH20260714-04124	system	2026-07-14 12:01:12.72+00
71	9000000104	COMMISSION	28000.00	3249dd29-eeb0-41b9-a360-66ad4c5890ea	Hoa hồng đơn DH20260807-B5BB7	system	2026-08-07 18:12:07.2+00
72	9000000104	COD_OWED	-300000.00	3249dd29-eeb0-41b9-a360-66ad4c5890ea	Shipper đã thu COD đơn DH20260807-B5BB7	system	2026-08-07 18:12:07.2+00
73	9000000105	COMMISSION	36000.00	18aa1028-1fb5-4643-b44e-1581931ff4e9	Hoa hồng đơn DH20260804-00CC7	system	2026-08-04 14:01:04.8+00
74	9000000105	COD_OWED	-155000.00	18aa1028-1fb5-4643-b44e-1581931ff4e9	Shipper đã thu COD đơn DH20260804-00CC7	system	2026-08-04 14:01:04.8+00
75	9000000101	COMMISSION	40000.00	96d4dcd2-7a41-42b5-bbfa-2ffeda0243a1	Hoa hồng đơn DH20260725-471AD	system	2026-07-25 14:27:40.08+00
76	9000000104	COMMISSION	24000.00	f881d853-eb35-446c-bbb3-7838c420760d	Hoa hồng đơn DH20260729-473E5	system	2026-07-29 21:56:56.4+00
77	9000000102	COMMISSION	20000.00	b31179de-25cd-4900-afc8-748d58e1c685	Hoa hồng đơn DH20260714-7AC3D	system	2026-07-14 15:05:30.72+00
78	9000000102	COD_OWED	-85000.00	b31179de-25cd-4900-afc8-748d58e1c685	Shipper đã thu COD đơn DH20260714-7AC3D	system	2026-07-14 15:05:30.72+00
79	9000000105	COMMISSION	20000.00	8d1f6598-0a74-4cde-955a-c81b5e646061	Hoa hồng đơn DH20260810-190CB	system	2026-08-10 09:13:03.36+00
80	9000000105	COD_OWED	-330000.00	8d1f6598-0a74-4cde-955a-c81b5e646061	Shipper đã thu COD đơn DH20260810-190CB	system	2026-08-10 09:13:03.36+00
81	9000000101	COMMISSION	24000.00	969e78cf-0b71-4f29-9fa7-bff1f6f01bdb	Hoa hồng đơn DH20260714-62F68	system	2026-07-14 20:00:28.32+00
82	9000000105	COMMISSION	40000.00	3a8f8ae6-68d1-40f8-8f29-7343da049169	Hoa hồng đơn DH20260726-13818	system	2026-07-26 17:31:19.2+00
83	9000000105	COMMISSION	16000.00	dede1587-6e75-4485-b136-abcd2f2b232c	Hoa hồng đơn DH20260729-6E5D1	system	2026-07-29 18:24:14.64+00
84	9000000105	COD_OWED	-110000.00	dede1587-6e75-4485-b136-abcd2f2b232c	Shipper đã thu COD đơn DH20260729-6E5D1	system	2026-07-29 18:24:14.64+00
85	9000000105	COMMISSION	20000.00	641f4e85-e1f2-4ae0-a15e-7d12cae5727d	Hoa hồng đơn DH20260728-16484	system	2026-07-28 12:23:11.04+00
86	9000000105	COD_OWED	-165000.00	641f4e85-e1f2-4ae0-a15e-7d12cae5727d	Shipper đã thu COD đơn DH20260728-16484	system	2026-07-28 12:23:11.04+00
87	9000000101	COMMISSION	16000.00	7bff06a1-4b9d-435f-b0ce-f5dffadfcb91	Hoa hồng đơn DH20260723-04C1A	system	2026-07-23 18:41:55.68+00
88	9000000101	COD_OWED	-295000.00	7bff06a1-4b9d-435f-b0ce-f5dffadfcb91	Shipper đã thu COD đơn DH20260723-04C1A	system	2026-07-23 18:41:55.68+00
89	9000000105	COMMISSION	40000.00	f38b2ebd-8cd5-4dd4-afbb-da238f64cd82	Hoa hồng đơn DH20260712-804DB	system	2026-07-12 18:50:36+00
90	9000000105	COMMISSION	40000.00	33d83c92-790e-4d27-bf3e-2acab5908b0b	Hoa hồng đơn DH20260726-3F5F2	system	2026-07-26 09:28:51.12+00
91	9000000102	COMMISSION	16000.00	2d234eb9-1a84-4b8e-acec-a2efd1c7f3ad	Hoa hồng đơn DH20260714-E48AD	system	2026-07-14 10:39:54.72+00
92	9000000102	COD_OWED	-150000.00	2d234eb9-1a84-4b8e-acec-a2efd1c7f3ad	Shipper đã thu COD đơn DH20260714-E48AD	system	2026-07-14 10:39:54.72+00
93	9000000101	COMMISSION	24000.00	80df0f44-9540-40ad-b325-2d62da3f93af	Hoa hồng đơn DH20260717-AD934	system	2026-07-17 15:24:17.76+00
94	9000000101	COD_OWED	-335000.00	80df0f44-9540-40ad-b325-2d62da3f93af	Shipper đã thu COD đơn DH20260717-AD934	system	2026-07-17 15:24:17.76+00
95	9000000104	COMMISSION	32000.00	761362f6-9711-471b-8fbc-c897ef2d0c02	Hoa hồng đơn DH20260713-48CE9	system	2026-07-13 19:32:47.28+00
96	9000000104	COD_OWED	-330000.00	761362f6-9711-471b-8fbc-c897ef2d0c02	Shipper đã thu COD đơn DH20260713-48CE9	system	2026-07-13 19:32:47.28+00
97	9000000101	COMMISSION	36000.00	534f8b1a-1248-4d07-a315-0f7781edc41b	Hoa hồng đơn DH20260724-9B7BE	system	2026-07-24 15:10:30+00
98	9000000101	COD_OWED	-125000.00	534f8b1a-1248-4d07-a315-0f7781edc41b	Shipper đã thu COD đơn DH20260724-9B7BE	system	2026-07-24 15:10:30+00
99	9000000101	COMMISSION	28000.00	b3868885-f520-46ba-93fc-22ee350d22d0	Hoa hồng đơn DH20260724-44BAA	system	2026-07-24 12:12:05.28+00
100	9000000101	COD_OWED	-60000.00	b3868885-f520-46ba-93fc-22ee350d22d0	Shipper đã thu COD đơn DH20260724-44BAA	system	2026-07-24 12:12:05.28+00
101	9000000102	COMMISSION	28000.00	0010521a-9d1e-4b44-9eb8-e450dc153c52	Hoa hồng đơn DH20260722-F9BCD	system	2026-07-22 19:20:00.72+00
102	9000000102	COD_OWED	-145000.00	0010521a-9d1e-4b44-9eb8-e450dc153c52	Shipper đã thu COD đơn DH20260722-F9BCD	system	2026-07-22 19:20:00.72+00
103	9000000105	COMMISSION	40000.00	4ca4e8e4-fd31-45f5-8828-fdf505c3ffd4	Hoa hồng đơn DH20260813-D699F	system	2026-08-13 06:59:00.919639+00
104	9000000105	COD_OWED	-75000.00	4ca4e8e4-fd31-45f5-8828-fdf505c3ffd4	Shipper đã thu COD đơn DH20260813-D699F	system	2026-08-13 06:59:00.919639+00
105	9000000101	SETTLEMENT_PAYOUT	-160800.00	\N	Đối soát kỳ 1 — shop thanh toán hoa hồng	admin	2026-07-30 07:43:35.719639+00
106	9000000102	SETTLEMENT_PAYOUT	-206400.00	\N	Đối soát kỳ 1 — shop thanh toán hoa hồng	admin	2026-07-30 07:43:35.719639+00
107	9000000104	SETTLEMENT_PAYOUT	-156000.00	\N	Đối soát kỳ 1 — shop thanh toán hoa hồng	admin	2026-07-30 07:43:35.719639+00
108	9000000105	SETTLEMENT_PAYOUT	-117600.00	\N	Đối soát kỳ 1 — shop thanh toán hoa hồng	admin	2026-07-30 07:43:35.719639+00
109	9000000101	SETTLEMENT_DEPOSIT	489000.00	\N	Đối soát kỳ 1 — shipper nộp tiền COD	admin	2026-07-30 07:43:35.719639+00
110	9000000102	SETTLEMENT_DEPOSIT	1284000.00	\N	Đối soát kỳ 1 — shipper nộp tiền COD	admin	2026-07-30 07:43:35.719639+00
111	9000000104	SETTLEMENT_DEPOSIT	483000.00	\N	Đối soát kỳ 1 — shipper nộp tiền COD	admin	2026-07-30 07:43:35.719639+00
112	9000000105	SETTLEMENT_DEPOSIT	165000.00	\N	Đối soát kỳ 1 — shipper nộp tiền COD	admin	2026-07-30 07:43:35.719639+00
\.


--
-- Data for Name: shipper_profile; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.shipper_profile (user_id, vehicle_type, license_plate, current_state, rating_avg, rating_count, total_deliveries, updated_at) FROM stdin;
9000000103	BICYCLE		OFFLINE	0.00	0	0	2026-08-13 07:43:35.459347+00
9000000101	MOTORBIKE	29-X1 12345	AVAILABLE	3.88	8	13	2026-08-13 07:43:35.459347+00
9000000102	MOTORBIKE	29-X2 23456	OFFLINE	4.70	10	19	2026-08-13 07:43:35.459347+00
9000000104	MOTORBIKE	29-B1 456.78	AVAILABLE	4.54	13	16	2026-08-13 07:43:35.71054+00
9000000105	MOTORBIKE	30-F5 789.12	AVAILABLE	4.54	13	16	2026-08-13 07:43:35.71054+00
9000000106	MOTORBIKE	29-C1 234.56	OFFLINE	0.00	0	0	2026-08-13 07:43:35.71054+00
\.


--
-- Data for Name: shipper_rating; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.shipper_rating (id, order_id, shipper_id, customer_id, stars, comment, created_at) FROM stdin;
\.


--
-- Data for Name: shop_config; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.shop_config (id, name, tagline, logo_url, brand_primary, brand_secondary, contact_phone, contact_email, opening_hours, pickup_lat, pickup_lng, pickup_address, fee_base, fee_per_km, free_km, updated_at, shipper_commission_pct) FROM stdin;
1	Shop Giao Hàng	Giao đồ ăn nhanh • Thanh toán dễ	\N	#D97706	#FB923C	\N	\N	08:00 - 22:00 hằng ngày	21.0285000	105.8542000	Shop default	15000.00	5000.00	0.000	2026-08-13 07:43:35.55093+00	80.00
\.


--
-- Data for Name: status_history; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.status_history (id, order_id, from_status, to_status, changed_by_user_id, changed_at, note) FROM stdin;
31	ef7351ae-470c-4e0a-b7b5-38c30103954e	\N	PENDING	9000001008	2026-08-03 18:20:00+00	Đơn được tạo
32	ef7351ae-470c-4e0a-b7b5-38c30103954e	PENDING	CONFIRMED	\N	2026-08-03 18:31:00+00	Shop xác nhận
33	ef7351ae-470c-4e0a-b7b5-38c30103954e	CONFIRMED	ASSIGNED	\N	2026-08-03 18:38:00+00	Gán shipper
34	ef7351ae-470c-4e0a-b7b5-38c30103954e	ASSIGNED	DELIVERING	9000000104	2026-08-03 18:51:00+00	Shipper bắt đầu giao
35	ef7351ae-470c-4e0a-b7b5-38c30103954e	DELIVERING	DELIVERED	9000000104	2026-08-03 19:17:31.44+00	Giao thành công
36	cf485f7a-c0d8-4e2f-b684-ae10bb5c10d5	\N	PENDING	9000001006	2026-07-23 19:55:00+00	Đơn được tạo
37	cf485f7a-c0d8-4e2f-b684-ae10bb5c10d5	PENDING	CONFIRMED	\N	2026-07-23 20:02:00+00	Shop xác nhận
38	cf485f7a-c0d8-4e2f-b684-ae10bb5c10d5	CONFIRMED	ASSIGNED	\N	2026-07-23 20:08:00+00	Gán shipper
39	cf485f7a-c0d8-4e2f-b684-ae10bb5c10d5	ASSIGNED	DELIVERING	9000000102	2026-07-23 20:22:00+00	Shipper bắt đầu giao
40	cf485f7a-c0d8-4e2f-b684-ae10bb5c10d5	DELIVERING	DELIVERED	9000000102	2026-07-23 21:00:50.4+00	Giao thành công
41	95cd2744-9d5a-4846-a675-8eff8f3a0e23	\N	PENDING	9000001007	2026-08-09 17:41:00+00	Đơn được tạo
42	95cd2744-9d5a-4846-a675-8eff8f3a0e23	PENDING	CONFIRMED	\N	2026-08-09 17:44:00+00	Shop xác nhận
43	95cd2744-9d5a-4846-a675-8eff8f3a0e23	CONFIRMED	ASSIGNED	\N	2026-08-09 17:46:00+00	Gán shipper
44	95cd2744-9d5a-4846-a675-8eff8f3a0e23	ASSIGNED	DELIVERING	9000000105	2026-08-09 17:55:00+00	Shipper bắt đầu giao
45	95cd2744-9d5a-4846-a675-8eff8f3a0e23	DELIVERING	DELIVERED	9000000105	2026-08-09 18:29:50.4+00	Giao thành công
46	e5d75285-641a-404c-b22d-ed3ce396da96	\N	PENDING	9000001001	2026-07-17 12:43:00+00	Đơn được tạo
47	e5d75285-641a-404c-b22d-ed3ce396da96	PENDING	CONFIRMED	\N	2026-07-17 12:55:00+00	Shop xác nhận
48	e5d75285-641a-404c-b22d-ed3ce396da96	CONFIRMED	ASSIGNED	\N	2026-07-17 12:57:00+00	Gán shipper
49	e5d75285-641a-404c-b22d-ed3ce396da96	ASSIGNED	DELIVERING	9000000102	2026-07-17 13:09:00+00	Shipper bắt đầu giao
50	e5d75285-641a-404c-b22d-ed3ce396da96	DELIVERING	DELIVERED	9000000102	2026-07-17 13:45:54+00	Giao thành công
51	07a155a6-f0b2-44f6-a4ad-82d05d35d3ec	\N	PENDING	9000000002	2026-08-09 14:16:00+00	Đơn được tạo
52	07a155a6-f0b2-44f6-a4ad-82d05d35d3ec	PENDING	CONFIRMED	\N	2026-08-09 14:28:00+00	Shop xác nhận
53	07a155a6-f0b2-44f6-a4ad-82d05d35d3ec	CONFIRMED	ASSIGNED	\N	2026-08-09 14:32:00+00	Gán shipper
54	07a155a6-f0b2-44f6-a4ad-82d05d35d3ec	ASSIGNED	DELIVERING	9000000104	2026-08-09 14:40:00+00	Shipper bắt đầu giao
55	07a155a6-f0b2-44f6-a4ad-82d05d35d3ec	DELIVERING	DELIVERED	9000000104	2026-08-09 15:30:19.68+00	Giao thành công
56	69bb8ac8-0f19-42b1-9139-1093646bcbcf	\N	PENDING	9000000003	2026-07-25 12:57:00+00	Đơn được tạo
57	69bb8ac8-0f19-42b1-9139-1093646bcbcf	PENDING	CANCELLED	\N	2026-07-25 13:13:00+00	Khách đổi ý
58	ccce1074-9c82-45c9-b742-a13a4aa32819	\N	PENDING	9000001009	2026-08-07 16:21:00+00	Đơn được tạo
59	ccce1074-9c82-45c9-b742-a13a4aa32819	PENDING	CONFIRMED	\N	2026-08-07 16:25:00+00	Shop xác nhận
60	ccce1074-9c82-45c9-b742-a13a4aa32819	CONFIRMED	ASSIGNED	\N	2026-08-07 16:28:00+00	Gán shipper
61	ccce1074-9c82-45c9-b742-a13a4aa32819	ASSIGNED	DELIVERING	9000000102	2026-08-07 16:40:00+00	Shipper bắt đầu giao
62	ccce1074-9c82-45c9-b742-a13a4aa32819	DELIVERING	DELIVERED	9000000102	2026-08-07 17:11:46.8+00	Giao thành công
63	e522f7bf-5a5b-4403-9534-a7c52c639537	\N	PENDING	9000001006	2026-07-26 20:08:00+00	Đơn được tạo
64	e522f7bf-5a5b-4403-9534-a7c52c639537	PENDING	CONFIRMED	\N	2026-07-26 20:19:00+00	Shop xác nhận
65	e522f7bf-5a5b-4403-9534-a7c52c639537	CONFIRMED	ASSIGNED	\N	2026-07-26 20:26:00+00	Gán shipper
66	e522f7bf-5a5b-4403-9534-a7c52c639537	ASSIGNED	DELIVERING	9000000104	2026-07-26 20:37:00+00	Shipper bắt đầu giao
67	e522f7bf-5a5b-4403-9534-a7c52c639537	DELIVERING	DELIVERED	9000000104	2026-07-26 21:05:38.4+00	Giao thành công
68	58b1c5da-2487-4332-a0f5-0406d45f6fb6	\N	PENDING	9000001005	2026-08-04 08:29:00+00	Đơn được tạo
69	58b1c5da-2487-4332-a0f5-0406d45f6fb6	PENDING	CONFIRMED	\N	2026-08-04 08:37:00+00	Shop xác nhận
70	58b1c5da-2487-4332-a0f5-0406d45f6fb6	CONFIRMED	ASSIGNED	\N	2026-08-04 08:39:00+00	Gán shipper
71	58b1c5da-2487-4332-a0f5-0406d45f6fb6	ASSIGNED	DELIVERING	9000000102	2026-08-04 08:48:00+00	Shipper bắt đầu giao
72	58b1c5da-2487-4332-a0f5-0406d45f6fb6	DELIVERING	DELIVERED	9000000102	2026-08-04 09:14:37.44+00	Giao thành công
73	65d924e2-e7a9-4eb9-b2b1-9782031b146f	\N	PENDING	9000001006	2026-07-22 10:29:00+00	Đơn được tạo
74	65d924e2-e7a9-4eb9-b2b1-9782031b146f	PENDING	CONFIRMED	\N	2026-07-22 10:37:00+00	Shop xác nhận
75	65d924e2-e7a9-4eb9-b2b1-9782031b146f	CONFIRMED	ASSIGNED	\N	2026-07-22 10:41:00+00	Gán shipper
76	65d924e2-e7a9-4eb9-b2b1-9782031b146f	ASSIGNED	DELIVERING	9000000101	2026-07-22 10:52:00+00	Shipper bắt đầu giao
77	65d924e2-e7a9-4eb9-b2b1-9782031b146f	DELIVERING	DELIVERED	9000000101	2026-07-22 11:41:02.4+00	Giao thành công
78	4424346a-3dd9-4084-8fb6-20fc52b977ed	\N	PENDING	9000001008	2026-07-26 17:43:00+00	Đơn được tạo
79	4424346a-3dd9-4084-8fb6-20fc52b977ed	PENDING	CONFIRMED	\N	2026-07-26 17:53:00+00	Shop xác nhận
80	4424346a-3dd9-4084-8fb6-20fc52b977ed	CONFIRMED	ASSIGNED	\N	2026-07-26 18:00:00+00	Gán shipper
81	4424346a-3dd9-4084-8fb6-20fc52b977ed	ASSIGNED	DELIVERING	9000000104	2026-07-26 18:08:00+00	Shipper bắt đầu giao
82	4424346a-3dd9-4084-8fb6-20fc52b977ed	DELIVERING	DELIVERED	9000000104	2026-07-26 18:48:24.24+00	Giao thành công
83	ce9f4b64-25f0-4bf6-86d6-8bdb6af5f1f6	\N	PENDING	9000001003	2026-07-14 19:05:00+00	Đơn được tạo
84	ce9f4b64-25f0-4bf6-86d6-8bdb6af5f1f6	PENDING	CONFIRMED	\N	2026-07-14 19:10:00+00	Shop xác nhận
85	ce9f4b64-25f0-4bf6-86d6-8bdb6af5f1f6	CONFIRMED	ASSIGNED	\N	2026-07-14 19:13:00+00	Gán shipper
86	ce9f4b64-25f0-4bf6-86d6-8bdb6af5f1f6	ASSIGNED	DELIVERING	9000000102	2026-07-14 19:21:00+00	Shipper bắt đầu giao
87	ce9f4b64-25f0-4bf6-86d6-8bdb6af5f1f6	DELIVERING	DELIVERED	9000000102	2026-07-14 19:55:45.12+00	Giao thành công
88	1371d53b-f0ec-49fc-ac00-64e5ddc7d66a	\N	PENDING	9000001003	2026-07-28 16:21:00+00	Đơn được tạo
89	1371d53b-f0ec-49fc-ac00-64e5ddc7d66a	PENDING	CONFIRMED	\N	2026-07-28 16:24:00+00	Shop xác nhận
90	1371d53b-f0ec-49fc-ac00-64e5ddc7d66a	CONFIRMED	ASSIGNED	\N	2026-07-28 16:26:00+00	Gán shipper
91	1371d53b-f0ec-49fc-ac00-64e5ddc7d66a	ASSIGNED	DELIVERING	9000000105	2026-07-28 16:39:00+00	Shipper bắt đầu giao
92	1371d53b-f0ec-49fc-ac00-64e5ddc7d66a	DELIVERING	DELIVERED	9000000105	2026-07-28 17:33:49.44+00	Giao thành công
93	f539a609-fa6a-4e2b-85eb-6b1d8ed9e2fd	\N	PENDING	9000001009	2026-07-19 20:52:00+00	Đơn được tạo
94	f539a609-fa6a-4e2b-85eb-6b1d8ed9e2fd	PENDING	CONFIRMED	\N	2026-07-19 20:58:00+00	Shop xác nhận
95	f539a609-fa6a-4e2b-85eb-6b1d8ed9e2fd	CONFIRMED	ASSIGNED	\N	2026-07-19 21:05:00+00	Gán shipper
96	f539a609-fa6a-4e2b-85eb-6b1d8ed9e2fd	ASSIGNED	DELIVERING	9000000104	2026-07-19 21:10:00+00	Shipper bắt đầu giao
97	f539a609-fa6a-4e2b-85eb-6b1d8ed9e2fd	DELIVERING	DELIVERED	9000000104	2026-07-19 21:52:05.52+00	Giao thành công
98	927ca6be-5d68-4abf-be79-c4b847b68df9	\N	PENDING	9000001009	2026-08-08 15:51:00+00	Đơn được tạo
99	927ca6be-5d68-4abf-be79-c4b847b68df9	PENDING	CONFIRMED	\N	2026-08-08 15:58:00+00	Shop xác nhận
100	927ca6be-5d68-4abf-be79-c4b847b68df9	CONFIRMED	ASSIGNED	\N	2026-08-08 16:04:00+00	Gán shipper
101	927ca6be-5d68-4abf-be79-c4b847b68df9	ASSIGNED	DELIVERING	9000000102	2026-08-08 16:19:00+00	Shipper bắt đầu giao
102	927ca6be-5d68-4abf-be79-c4b847b68df9	DELIVERING	DELIVERED	9000000102	2026-08-08 17:13:53.28+00	Giao thành công
103	c0aa3bb4-9c7e-4795-9e47-81328313713b	\N	PENDING	9000001003	2026-07-12 09:50:00+00	Đơn được tạo
104	c0aa3bb4-9c7e-4795-9e47-81328313713b	PENDING	CONFIRMED	\N	2026-07-12 09:55:00+00	Shop xác nhận
105	c0aa3bb4-9c7e-4795-9e47-81328313713b	CONFIRMED	ASSIGNED	\N	2026-07-12 09:58:00+00	Gán shipper
106	c0aa3bb4-9c7e-4795-9e47-81328313713b	ASSIGNED	DELIVERING	9000000104	2026-07-12 10:05:00+00	Shipper bắt đầu giao
107	c0aa3bb4-9c7e-4795-9e47-81328313713b	DELIVERING	DELIVERED	9000000104	2026-07-12 10:47:06.48+00	Giao thành công
108	09f4f7ac-58af-4dee-885a-12fda8414336	\N	PENDING	9000001009	2026-08-01 08:56:00+00	Đơn được tạo
109	09f4f7ac-58af-4dee-885a-12fda8414336	PENDING	CONFIRMED	\N	2026-08-01 09:00:00+00	Shop xác nhận
110	09f4f7ac-58af-4dee-885a-12fda8414336	CONFIRMED	ASSIGNED	\N	2026-08-01 09:03:00+00	Gán shipper
111	09f4f7ac-58af-4dee-885a-12fda8414336	ASSIGNED	DELIVERING	9000000105	2026-08-01 09:12:00+00	Shipper bắt đầu giao
112	09f4f7ac-58af-4dee-885a-12fda8414336	DELIVERING	DELIVERED	9000000105	2026-08-01 09:50:11.04+00	Giao thành công
113	3f0cc202-05d2-4496-bef2-34626ed412aa	\N	PENDING	9000001001	2026-08-02 18:47:00+00	Đơn được tạo
114	3f0cc202-05d2-4496-bef2-34626ed412aa	PENDING	CANCELLED	\N	2026-08-02 19:06:00+00	Khách đổi ý
115	7ddabb80-7b7a-4a4d-9c58-c0203c31fc46	\N	PENDING	9000001007	2026-07-24 10:29:00+00	Đơn được tạo
116	7ddabb80-7b7a-4a4d-9c58-c0203c31fc46	PENDING	CONFIRMED	\N	2026-07-24 10:40:00+00	Shop xác nhận
117	7ddabb80-7b7a-4a4d-9c58-c0203c31fc46	CONFIRMED	ASSIGNED	\N	2026-07-24 10:45:00+00	Gán shipper
118	7ddabb80-7b7a-4a4d-9c58-c0203c31fc46	ASSIGNED	DELIVERING	9000000104	2026-07-24 10:52:00+00	Shipper bắt đầu giao
119	7ddabb80-7b7a-4a4d-9c58-c0203c31fc46	DELIVERING	DELIVERED	9000000104	2026-07-24 11:29:18.48+00	Giao thành công
120	54e966e2-4009-4bf8-ad7f-68413dce74a1	\N	PENDING	9000000001	2026-07-28 08:11:00+00	Đơn được tạo
121	54e966e2-4009-4bf8-ad7f-68413dce74a1	PENDING	CONFIRMED	\N	2026-07-28 08:19:00+00	Shop xác nhận
122	54e966e2-4009-4bf8-ad7f-68413dce74a1	CONFIRMED	ASSIGNED	\N	2026-07-28 08:24:00+00	Gán shipper
123	54e966e2-4009-4bf8-ad7f-68413dce74a1	ASSIGNED	DELIVERING	9000000102	2026-07-28 08:36:00+00	Shipper bắt đầu giao
124	54e966e2-4009-4bf8-ad7f-68413dce74a1	DELIVERING	DELIVERED	9000000102	2026-07-28 08:51:52.32+00	Giao thành công
125	9d94bef9-0883-414b-8af1-279b195670fc	\N	PENDING	9000001005	2026-08-09 15:30:00+00	Đơn được tạo
126	9d94bef9-0883-414b-8af1-279b195670fc	PENDING	CONFIRMED	\N	2026-08-09 15:40:00+00	Shop xác nhận
127	9d94bef9-0883-414b-8af1-279b195670fc	CONFIRMED	ASSIGNED	\N	2026-08-09 15:43:00+00	Gán shipper
128	9d94bef9-0883-414b-8af1-279b195670fc	ASSIGNED	DELIVERING	9000000101	2026-08-09 15:54:00+00	Shipper bắt đầu giao
129	9d94bef9-0883-414b-8af1-279b195670fc	DELIVERING	DELIVERED	9000000101	2026-08-09 16:26:24.72+00	Giao thành công
130	e3864da8-2daf-4740-b0d7-095804a16de2	\N	PENDING	9000000001	2026-08-07 16:02:00+00	Đơn được tạo
131	e3864da8-2daf-4740-b0d7-095804a16de2	PENDING	CONFIRMED	\N	2026-08-07 16:14:00+00	Shop xác nhận
132	e3864da8-2daf-4740-b0d7-095804a16de2	CONFIRMED	ASSIGNED	\N	2026-08-07 16:20:00+00	Gán shipper
133	e3864da8-2daf-4740-b0d7-095804a16de2	ASSIGNED	DELIVERING	9000000102	2026-08-07 16:27:00+00	Shipper bắt đầu giao
134	e3864da8-2daf-4740-b0d7-095804a16de2	DELIVERING	DELIVERED	9000000102	2026-08-07 16:54:06+00	Giao thành công
135	7cc0b027-15f7-4145-90cf-835ec0553793	\N	PENDING	9000001004	2026-08-03 10:34:00+00	Đơn được tạo
136	7cc0b027-15f7-4145-90cf-835ec0553793	PENDING	CONFIRMED	\N	2026-08-03 10:42:00+00	Shop xác nhận
137	7cc0b027-15f7-4145-90cf-835ec0553793	CONFIRMED	ASSIGNED	\N	2026-08-03 10:44:00+00	Gán shipper
138	7cc0b027-15f7-4145-90cf-835ec0553793	ASSIGNED	DELIVERING	9000000101	2026-08-03 10:56:00+00	Shipper bắt đầu giao
139	7cc0b027-15f7-4145-90cf-835ec0553793	DELIVERING	DELIVERED	9000000101	2026-08-03 11:43:27.36+00	Giao thành công
140	9cbcb6d1-8dbf-454a-9bc0-8f236b81c735	\N	PENDING	9000001007	2026-07-24 17:56:00+00	Đơn được tạo
141	9cbcb6d1-8dbf-454a-9bc0-8f236b81c735	PENDING	CONFIRMED	\N	2026-07-24 18:07:00+00	Shop xác nhận
142	9cbcb6d1-8dbf-454a-9bc0-8f236b81c735	CONFIRMED	ASSIGNED	\N	2026-07-24 18:09:00+00	Gán shipper
143	9cbcb6d1-8dbf-454a-9bc0-8f236b81c735	ASSIGNED	DELIVERING	9000000102	2026-07-24 18:17:00+00	Shipper bắt đầu giao
144	9cbcb6d1-8dbf-454a-9bc0-8f236b81c735	DELIVERING	DELIVERED	9000000102	2026-07-24 18:52:33.12+00	Giao thành công
145	12858304-2565-4efa-ac44-967e3754d616	\N	PENDING	9000001006	2026-07-28 20:17:00+00	Đơn được tạo
146	12858304-2565-4efa-ac44-967e3754d616	PENDING	CONFIRMED	\N	2026-07-28 20:25:00+00	Shop xác nhận
147	12858304-2565-4efa-ac44-967e3754d616	CONFIRMED	ASSIGNED	\N	2026-07-28 20:31:00+00	Gán shipper
148	12858304-2565-4efa-ac44-967e3754d616	ASSIGNED	DELIVERING	9000000102	2026-07-28 20:44:00+00	Shipper bắt đầu giao
149	12858304-2565-4efa-ac44-967e3754d616	DELIVERING	DELIVERED	9000000102	2026-07-28 21:16:24.24+00	Giao thành công
150	ba3e4076-c563-42b7-ba2b-4bfa199b57df	\N	PENDING	9000001010	2026-07-18 17:25:00+00	Đơn được tạo
151	ba3e4076-c563-42b7-ba2b-4bfa199b57df	PENDING	CONFIRMED	\N	2026-07-18 17:30:00+00	Shop xác nhận
152	ba3e4076-c563-42b7-ba2b-4bfa199b57df	CONFIRMED	ASSIGNED	\N	2026-07-18 17:32:00+00	Gán shipper
153	ba3e4076-c563-42b7-ba2b-4bfa199b57df	ASSIGNED	DELIVERING	9000000101	2026-07-18 17:37:00+00	Shipper bắt đầu giao
154	ba3e4076-c563-42b7-ba2b-4bfa199b57df	DELIVERING	DELIVERED	9000000101	2026-07-18 18:06:14.64+00	Giao thành công
155	156d9c3b-a75a-4ffa-9c0c-65d5620cdcbc	\N	PENDING	9000001008	2026-07-17 11:00:00+00	Đơn được tạo
156	156d9c3b-a75a-4ffa-9c0c-65d5620cdcbc	PENDING	CONFIRMED	\N	2026-07-17 11:06:00+00	Shop xác nhận
157	156d9c3b-a75a-4ffa-9c0c-65d5620cdcbc	CONFIRMED	ASSIGNED	\N	2026-07-17 11:10:00+00	Gán shipper
158	156d9c3b-a75a-4ffa-9c0c-65d5620cdcbc	ASSIGNED	DELIVERING	9000000102	2026-07-17 11:21:00+00	Shipper bắt đầu giao
159	156d9c3b-a75a-4ffa-9c0c-65d5620cdcbc	DELIVERING	DELIVERED	9000000102	2026-07-17 12:07:17.76+00	Giao thành công
160	feed5ba3-8777-4c41-abb3-d6379437e71c	\N	PENDING	9000001007	2026-08-05 19:10:00+00	Đơn được tạo
161	feed5ba3-8777-4c41-abb3-d6379437e71c	PENDING	CONFIRMED	\N	2026-08-05 19:22:00+00	Shop xác nhận
162	feed5ba3-8777-4c41-abb3-d6379437e71c	CONFIRMED	ASSIGNED	\N	2026-08-05 19:24:00+00	Gán shipper
163	feed5ba3-8777-4c41-abb3-d6379437e71c	ASSIGNED	DELIVERING	9000000102	2026-08-05 19:33:00+00	Shipper bắt đầu giao
164	feed5ba3-8777-4c41-abb3-d6379437e71c	DELIVERING	DELIVERED	9000000102	2026-08-05 20:26:28.32+00	Giao thành công
165	1aa7c754-bb0e-4e83-b947-9c17aeb65e53	\N	PENDING	9000001005	2026-08-04 16:17:00+00	Đơn được tạo
166	1aa7c754-bb0e-4e83-b947-9c17aeb65e53	PENDING	CONFIRMED	\N	2026-08-04 16:21:00+00	Shop xác nhận
167	1aa7c754-bb0e-4e83-b947-9c17aeb65e53	CONFIRMED	ASSIGNED	\N	2026-08-04 16:23:00+00	Gán shipper
168	1aa7c754-bb0e-4e83-b947-9c17aeb65e53	ASSIGNED	DELIVERING	9000000105	2026-08-04 16:33:00+00	Shipper bắt đầu giao
169	1aa7c754-bb0e-4e83-b947-9c17aeb65e53	DELIVERING	DELIVERED	9000000105	2026-08-04 16:49:23.04+00	Giao thành công
170	41c5b85b-1961-499c-9ec6-2a61eb71865f	\N	PENDING	9000001010	2026-07-31 08:40:00+00	Đơn được tạo
171	41c5b85b-1961-499c-9ec6-2a61eb71865f	PENDING	CONFIRMED	\N	2026-07-31 08:51:00+00	Shop xác nhận
172	41c5b85b-1961-499c-9ec6-2a61eb71865f	CONFIRMED	ASSIGNED	\N	2026-07-31 08:54:00+00	Gán shipper
173	41c5b85b-1961-499c-9ec6-2a61eb71865f	ASSIGNED	DELIVERING	9000000105	2026-07-31 09:09:00+00	Shipper bắt đầu giao
174	41c5b85b-1961-499c-9ec6-2a61eb71865f	DELIVERING	DELIVERED	9000000105	2026-07-31 09:42:23.76+00	Giao thành công
175	d5dbe7df-d262-49ef-b18f-13938b290901	\N	PENDING	9000001006	2026-08-05 10:01:00+00	Đơn được tạo
176	d5dbe7df-d262-49ef-b18f-13938b290901	PENDING	CONFIRMED	\N	2026-08-05 10:07:00+00	Shop xác nhận
177	d5dbe7df-d262-49ef-b18f-13938b290901	CONFIRMED	ASSIGNED	\N	2026-08-05 10:11:00+00	Gán shipper
178	d5dbe7df-d262-49ef-b18f-13938b290901	ASSIGNED	DELIVERING	9000000102	2026-08-05 10:22:00+00	Shipper bắt đầu giao
179	d5dbe7df-d262-49ef-b18f-13938b290901	DELIVERING	DELIVERED	9000000102	2026-08-05 10:56:47.04+00	Giao thành công
180	49b65a2e-19da-49a0-8964-cfc8ac32e005	\N	PENDING	9000001004	2026-07-14 12:22:00+00	Đơn được tạo
181	49b65a2e-19da-49a0-8964-cfc8ac32e005	PENDING	CONFIRMED	\N	2026-07-14 12:31:00+00	Shop xác nhận
182	49b65a2e-19da-49a0-8964-cfc8ac32e005	CONFIRMED	ASSIGNED	\N	2026-07-14 12:35:00+00	Gán shipper
183	49b65a2e-19da-49a0-8964-cfc8ac32e005	ASSIGNED	DELIVERING	9000000104	2026-07-14 12:46:00+00	Shipper bắt đầu giao
184	49b65a2e-19da-49a0-8964-cfc8ac32e005	DELIVERING	DELIVERED	9000000104	2026-07-14 13:36:06+00	Giao thành công
185	bf09be0c-bf44-45e6-91de-7505423d777c	\N	PENDING	9000001001	2026-07-13 19:43:00+00	Đơn được tạo
186	bf09be0c-bf44-45e6-91de-7505423d777c	PENDING	CONFIRMED	\N	2026-07-13 19:46:00+00	Shop xác nhận
187	bf09be0c-bf44-45e6-91de-7505423d777c	CONFIRMED	ASSIGNED	\N	2026-07-13 19:50:00+00	Gán shipper
188	bf09be0c-bf44-45e6-91de-7505423d777c	ASSIGNED	DELIVERING	9000000101	2026-07-13 20:05:00+00	Shipper bắt đầu giao
189	bf09be0c-bf44-45e6-91de-7505423d777c	DELIVERING	DELIVERED	9000000101	2026-07-13 20:47:40.32+00	Giao thành công
190	daf837c8-14c0-4eca-99a4-3fd9c9a3fa5c	\N	PENDING	9000001006	2026-08-03 10:57:00+00	Đơn được tạo
191	daf837c8-14c0-4eca-99a4-3fd9c9a3fa5c	PENDING	CONFIRMED	\N	2026-08-03 11:05:00+00	Shop xác nhận
192	daf837c8-14c0-4eca-99a4-3fd9c9a3fa5c	CONFIRMED	ASSIGNED	\N	2026-08-03 11:07:00+00	Gán shipper
193	daf837c8-14c0-4eca-99a4-3fd9c9a3fa5c	ASSIGNED	DELIVERING	9000000104	2026-08-03 11:14:00+00	Shipper bắt đầu giao
194	daf837c8-14c0-4eca-99a4-3fd9c9a3fa5c	DELIVERING	DELIVERED	9000000104	2026-08-03 11:46:36+00	Giao thành công
195	bb4e245f-83ae-4035-a2bf-7e2a411ceb6f	\N	PENDING	9000001004	2026-07-29 11:14:00+00	Đơn được tạo
196	bb4e245f-83ae-4035-a2bf-7e2a411ceb6f	PENDING	CONFIRMED	\N	2026-07-29 11:17:00+00	Shop xác nhận
197	bb4e245f-83ae-4035-a2bf-7e2a411ceb6f	CONFIRMED	ASSIGNED	\N	2026-07-29 11:23:00+00	Gán shipper
198	bb4e245f-83ae-4035-a2bf-7e2a411ceb6f	ASSIGNED	DELIVERING	9000000102	2026-07-29 11:35:00+00	Shipper bắt đầu giao
199	bb4e245f-83ae-4035-a2bf-7e2a411ceb6f	DELIVERING	DELIVERED	9000000102	2026-07-29 12:02:33.84+00	Giao thành công
200	e8231aad-69e7-4a8e-869f-ab897492cc12	\N	PENDING	9000001008	2026-07-26 16:51:00+00	Đơn được tạo
201	e8231aad-69e7-4a8e-869f-ab897492cc12	PENDING	CONFIRMED	\N	2026-07-26 17:02:00+00	Shop xác nhận
202	e8231aad-69e7-4a8e-869f-ab897492cc12	CONFIRMED	ASSIGNED	\N	2026-07-26 17:08:00+00	Gán shipper
203	e8231aad-69e7-4a8e-869f-ab897492cc12	ASSIGNED	DELIVERING	9000000104	2026-07-26 17:15:00+00	Shipper bắt đầu giao
204	e8231aad-69e7-4a8e-869f-ab897492cc12	DELIVERING	DELIVERED	9000000104	2026-07-26 17:34:54.24+00	Giao thành công
205	fe4ab8a9-5c7f-4d54-9515-5f1bab09fe60	\N	PENDING	9000001003	2026-08-02 11:39:00+00	Đơn được tạo
206	fe4ab8a9-5c7f-4d54-9515-5f1bab09fe60	PENDING	CONFIRMED	\N	2026-08-02 11:50:00+00	Shop xác nhận
207	fe4ab8a9-5c7f-4d54-9515-5f1bab09fe60	CONFIRMED	ASSIGNED	\N	2026-08-02 11:55:00+00	Gán shipper
208	fe4ab8a9-5c7f-4d54-9515-5f1bab09fe60	ASSIGNED	DELIVERING	9000000104	2026-08-02 12:04:00+00	Shipper bắt đầu giao
209	fe4ab8a9-5c7f-4d54-9515-5f1bab09fe60	DELIVERING	DELIVERED	9000000104	2026-08-02 12:35:16.8+00	Giao thành công
210	e511e288-d7b5-44bf-a6fa-76f0dfca259a	\N	PENDING	9000000003	2026-08-06 15:08:00+00	Đơn được tạo
211	e511e288-d7b5-44bf-a6fa-76f0dfca259a	PENDING	CONFIRMED	\N	2026-08-06 15:20:00+00	Shop xác nhận
212	e511e288-d7b5-44bf-a6fa-76f0dfca259a	CONFIRMED	ASSIGNED	\N	2026-08-06 15:26:00+00	Gán shipper
213	e511e288-d7b5-44bf-a6fa-76f0dfca259a	ASSIGNED	DELIVERING	9000000105	2026-08-06 15:38:00+00	Shipper bắt đầu giao
214	e511e288-d7b5-44bf-a6fa-76f0dfca259a	DELIVERING	DELIVERED	9000000105	2026-08-06 16:06:07.2+00	Giao thành công
215	f6eb20eb-aa59-45e8-80ee-eb835617bc04	\N	PENDING	9000001005	2026-08-06 16:55:00+00	Đơn được tạo
216	f6eb20eb-aa59-45e8-80ee-eb835617bc04	PENDING	CONFIRMED	\N	2026-08-06 17:05:00+00	Shop xác nhận
217	f6eb20eb-aa59-45e8-80ee-eb835617bc04	CONFIRMED	ASSIGNED	\N	2026-08-06 17:12:00+00	Gán shipper
218	f6eb20eb-aa59-45e8-80ee-eb835617bc04	ASSIGNED	DELIVERING	9000000104	2026-08-06 17:23:00+00	Shipper bắt đầu giao
219	f6eb20eb-aa59-45e8-80ee-eb835617bc04	DELIVERING	DELIVERED	9000000104	2026-08-06 18:05:55.68+00	Giao thành công
220	d107d669-b496-4dcc-ab4e-d2efb56f55cf	\N	PENDING	9000001007	2026-08-12 14:08:00+00	Đơn được tạo
221	d107d669-b496-4dcc-ab4e-d2efb56f55cf	PENDING	CONFIRMED	\N	2026-08-12 14:16:00+00	Shop xác nhận
222	d107d669-b496-4dcc-ab4e-d2efb56f55cf	CONFIRMED	ASSIGNED	\N	2026-08-12 14:23:00+00	Gán shipper
223	d107d669-b496-4dcc-ab4e-d2efb56f55cf	ASSIGNED	DELIVERING	9000000101	2026-08-12 14:33:00+00	Shipper bắt đầu giao
224	d107d669-b496-4dcc-ab4e-d2efb56f55cf	DELIVERING	DELIVERED	9000000101	2026-08-12 15:08:41.76+00	Giao thành công
225	709e8b53-d695-4537-89e5-3cb2e021b8f8	\N	PENDING	9000001001	2026-08-02 10:10:00+00	Đơn được tạo
226	709e8b53-d695-4537-89e5-3cb2e021b8f8	PENDING	CONFIRMED	\N	2026-08-02 10:22:00+00	Shop xác nhận
227	709e8b53-d695-4537-89e5-3cb2e021b8f8	CONFIRMED	ASSIGNED	\N	2026-08-02 10:24:00+00	Gán shipper
228	709e8b53-d695-4537-89e5-3cb2e021b8f8	ASSIGNED	DELIVERING	9000000105	2026-08-02 10:29:00+00	Shipper bắt đầu giao
229	709e8b53-d695-4537-89e5-3cb2e021b8f8	DELIVERING	DELIVERED	9000000105	2026-08-02 11:21:32.16+00	Giao thành công
230	7b0d565f-cfbf-451a-bba4-bb161c963675	\N	PENDING	9000001006	2026-08-04 14:14:00+00	Đơn được tạo
231	7b0d565f-cfbf-451a-bba4-bb161c963675	PENDING	CONFIRMED	\N	2026-08-04 14:23:00+00	Shop xác nhận
232	7b0d565f-cfbf-451a-bba4-bb161c963675	CONFIRMED	ASSIGNED	\N	2026-08-04 14:29:00+00	Gán shipper
233	7b0d565f-cfbf-451a-bba4-bb161c963675	ASSIGNED	DELIVERING	9000000104	2026-08-04 14:38:00+00	Shipper bắt đầu giao
234	7b0d565f-cfbf-451a-bba4-bb161c963675	DELIVERING	DELIVERED	9000000104	2026-08-04 14:59:51.36+00	Giao thành công
235	22c37396-e5be-4d5a-9c8d-f0682c1ef38e	\N	PENDING	9000000001	2026-07-12 13:10:00+00	Đơn được tạo
236	22c37396-e5be-4d5a-9c8d-f0682c1ef38e	PENDING	CONFIRMED	\N	2026-07-12 13:14:00+00	Shop xác nhận
237	22c37396-e5be-4d5a-9c8d-f0682c1ef38e	CONFIRMED	ASSIGNED	\N	2026-07-12 13:17:00+00	Gán shipper
238	22c37396-e5be-4d5a-9c8d-f0682c1ef38e	ASSIGNED	DELIVERING	9000000102	2026-07-12 13:31:00+00	Shipper bắt đầu giao
239	22c37396-e5be-4d5a-9c8d-f0682c1ef38e	DELIVERING	DELIVERED	9000000102	2026-07-12 14:23:27.12+00	Giao thành công
240	d5a97f80-1877-4c33-bffd-33567e09325f	\N	PENDING	9000001003	2026-08-10 09:18:00+00	Đơn được tạo
241	d5a97f80-1877-4c33-bffd-33567e09325f	PENDING	CONFIRMED	\N	2026-08-10 09:21:00+00	Shop xác nhận
242	d5a97f80-1877-4c33-bffd-33567e09325f	CONFIRMED	ASSIGNED	\N	2026-08-10 09:24:00+00	Gán shipper
243	d5a97f80-1877-4c33-bffd-33567e09325f	ASSIGNED	DELIVERING	9000000105	2026-08-10 09:31:00+00	Shipper bắt đầu giao
244	d5a97f80-1877-4c33-bffd-33567e09325f	DELIVERING	DELIVERED	9000000105	2026-08-10 09:59:43.68+00	Giao thành công
245	7a2c6725-0fd4-4b6f-afae-4f4fd6de24c0	\N	PENDING	9000001008	2026-08-12 11:24:00+00	Đơn được tạo
246	7a2c6725-0fd4-4b6f-afae-4f4fd6de24c0	PENDING	CONFIRMED	\N	2026-08-12 11:33:00+00	Shop xác nhận
247	7a2c6725-0fd4-4b6f-afae-4f4fd6de24c0	CONFIRMED	ASSIGNED	\N	2026-08-12 11:37:00+00	Gán shipper
248	7a2c6725-0fd4-4b6f-afae-4f4fd6de24c0	ASSIGNED	DELIVERING	9000000101	2026-08-12 11:45:00+00	Shipper bắt đầu giao
249	7a2c6725-0fd4-4b6f-afae-4f4fd6de24c0	DELIVERING	DELIVERED	9000000101	2026-08-12 12:23:36+00	Giao thành công
250	7cb9dd02-a9d6-4f9f-98d9-1a8a8f635e35	\N	PENDING	9000000002	2026-07-14 11:04:00+00	Đơn được tạo
251	7cb9dd02-a9d6-4f9f-98d9-1a8a8f635e35	PENDING	CONFIRMED	\N	2026-07-14 11:14:00+00	Shop xác nhận
252	7cb9dd02-a9d6-4f9f-98d9-1a8a8f635e35	CONFIRMED	ASSIGNED	\N	2026-07-14 11:18:00+00	Gán shipper
253	7cb9dd02-a9d6-4f9f-98d9-1a8a8f635e35	ASSIGNED	DELIVERING	9000000102	2026-07-14 11:23:00+00	Shipper bắt đầu giao
254	7cb9dd02-a9d6-4f9f-98d9-1a8a8f635e35	DELIVERING	DELIVERED	9000000102	2026-07-14 12:01:12.72+00	Giao thành công
255	3249dd29-eeb0-41b9-a360-66ad4c5890ea	\N	PENDING	9000001007	2026-08-07 17:09:00+00	Đơn được tạo
256	3249dd29-eeb0-41b9-a360-66ad4c5890ea	PENDING	CONFIRMED	\N	2026-08-07 17:20:00+00	Shop xác nhận
257	3249dd29-eeb0-41b9-a360-66ad4c5890ea	CONFIRMED	ASSIGNED	\N	2026-08-07 17:22:00+00	Gán shipper
258	3249dd29-eeb0-41b9-a360-66ad4c5890ea	ASSIGNED	DELIVERING	9000000104	2026-08-07 17:33:00+00	Shipper bắt đầu giao
259	3249dd29-eeb0-41b9-a360-66ad4c5890ea	DELIVERING	DELIVERED	9000000104	2026-08-07 18:12:07.2+00	Giao thành công
260	18aa1028-1fb5-4643-b44e-1581931ff4e9	\N	PENDING	9000001001	2026-08-04 12:57:00+00	Đơn được tạo
261	18aa1028-1fb5-4643-b44e-1581931ff4e9	PENDING	CONFIRMED	\N	2026-08-04 13:08:00+00	Shop xác nhận
262	18aa1028-1fb5-4643-b44e-1581931ff4e9	CONFIRMED	ASSIGNED	\N	2026-08-04 13:15:00+00	Gán shipper
263	18aa1028-1fb5-4643-b44e-1581931ff4e9	ASSIGNED	DELIVERING	9000000105	2026-08-04 13:22:00+00	Shipper bắt đầu giao
264	18aa1028-1fb5-4643-b44e-1581931ff4e9	DELIVERING	DELIVERED	9000000105	2026-08-04 14:01:04.8+00	Giao thành công
265	96d4dcd2-7a41-42b5-bbfa-2ffeda0243a1	\N	PENDING	9000001010	2026-07-25 13:17:00+00	Đơn được tạo
266	96d4dcd2-7a41-42b5-bbfa-2ffeda0243a1	PENDING	CONFIRMED	\N	2026-07-25 13:29:00+00	Shop xác nhận
267	96d4dcd2-7a41-42b5-bbfa-2ffeda0243a1	CONFIRMED	ASSIGNED	\N	2026-07-25 13:35:00+00	Gán shipper
268	96d4dcd2-7a41-42b5-bbfa-2ffeda0243a1	ASSIGNED	DELIVERING	9000000101	2026-07-25 13:46:00+00	Shipper bắt đầu giao
269	96d4dcd2-7a41-42b5-bbfa-2ffeda0243a1	DELIVERING	DELIVERED	9000000101	2026-07-25 14:27:40.08+00	Giao thành công
270	f881d853-eb35-446c-bbb3-7838c420760d	\N	PENDING	9000001002	2026-07-29 20:59:00+00	Đơn được tạo
271	f881d853-eb35-446c-bbb3-7838c420760d	PENDING	CONFIRMED	\N	2026-07-29 21:02:00+00	Shop xác nhận
272	f881d853-eb35-446c-bbb3-7838c420760d	CONFIRMED	ASSIGNED	\N	2026-07-29 21:07:00+00	Gán shipper
273	f881d853-eb35-446c-bbb3-7838c420760d	ASSIGNED	DELIVERING	9000000104	2026-07-29 21:22:00+00	Shipper bắt đầu giao
274	f881d853-eb35-446c-bbb3-7838c420760d	DELIVERING	DELIVERED	9000000104	2026-07-29 21:56:56.4+00	Giao thành công
275	b31179de-25cd-4900-afc8-748d58e1c685	\N	PENDING	9000000003	2026-07-14 14:13:00+00	Đơn được tạo
276	b31179de-25cd-4900-afc8-748d58e1c685	PENDING	CONFIRMED	\N	2026-07-14 14:22:00+00	Shop xác nhận
277	b31179de-25cd-4900-afc8-748d58e1c685	CONFIRMED	ASSIGNED	\N	2026-07-14 14:27:00+00	Gán shipper
278	b31179de-25cd-4900-afc8-748d58e1c685	ASSIGNED	DELIVERING	9000000102	2026-07-14 14:38:00+00	Shipper bắt đầu giao
279	b31179de-25cd-4900-afc8-748d58e1c685	DELIVERING	DELIVERED	9000000102	2026-07-14 15:05:30.72+00	Giao thành công
280	8d1f6598-0a74-4cde-955a-c81b5e646061	\N	PENDING	9000000003	2026-08-10 08:22:00+00	Đơn được tạo
281	8d1f6598-0a74-4cde-955a-c81b5e646061	PENDING	CONFIRMED	\N	2026-08-10 08:28:00+00	Shop xác nhận
282	8d1f6598-0a74-4cde-955a-c81b5e646061	CONFIRMED	ASSIGNED	\N	2026-08-10 08:30:00+00	Gán shipper
283	8d1f6598-0a74-4cde-955a-c81b5e646061	ASSIGNED	DELIVERING	9000000105	2026-08-10 08:43:00+00	Shipper bắt đầu giao
284	8d1f6598-0a74-4cde-955a-c81b5e646061	DELIVERING	DELIVERED	9000000105	2026-08-10 09:13:03.36+00	Giao thành công
285	969e78cf-0b71-4f29-9fa7-bff1f6f01bdb	\N	PENDING	9000001010	2026-07-14 19:12:00+00	Đơn được tạo
286	969e78cf-0b71-4f29-9fa7-bff1f6f01bdb	PENDING	CONFIRMED	\N	2026-07-14 19:17:00+00	Shop xác nhận
287	969e78cf-0b71-4f29-9fa7-bff1f6f01bdb	CONFIRMED	ASSIGNED	\N	2026-07-14 19:22:00+00	Gán shipper
288	969e78cf-0b71-4f29-9fa7-bff1f6f01bdb	ASSIGNED	DELIVERING	9000000101	2026-07-14 19:35:00+00	Shipper bắt đầu giao
289	969e78cf-0b71-4f29-9fa7-bff1f6f01bdb	DELIVERING	DELIVERED	9000000101	2026-07-14 20:00:28.32+00	Giao thành công
290	3a8f8ae6-68d1-40f8-8f29-7343da049169	\N	PENDING	9000001004	2026-07-26 16:26:00+00	Đơn được tạo
291	3a8f8ae6-68d1-40f8-8f29-7343da049169	PENDING	CONFIRMED	\N	2026-07-26 16:29:00+00	Shop xác nhận
292	3a8f8ae6-68d1-40f8-8f29-7343da049169	CONFIRMED	ASSIGNED	\N	2026-07-26 16:31:00+00	Gán shipper
293	3a8f8ae6-68d1-40f8-8f29-7343da049169	ASSIGNED	DELIVERING	9000000105	2026-07-26 16:45:00+00	Shipper bắt đầu giao
294	3a8f8ae6-68d1-40f8-8f29-7343da049169	DELIVERING	DELIVERED	9000000105	2026-07-26 17:31:19.2+00	Giao thành công
295	dede1587-6e75-4485-b136-abcd2f2b232c	\N	PENDING	9000001004	2026-07-29 17:54:00+00	Đơn được tạo
296	dede1587-6e75-4485-b136-abcd2f2b232c	PENDING	CONFIRMED	\N	2026-07-29 18:01:00+00	Shop xác nhận
297	dede1587-6e75-4485-b136-abcd2f2b232c	CONFIRMED	ASSIGNED	\N	2026-07-29 18:03:00+00	Gán shipper
298	dede1587-6e75-4485-b136-abcd2f2b232c	ASSIGNED	DELIVERING	9000000105	2026-07-29 18:10:00+00	Shipper bắt đầu giao
299	dede1587-6e75-4485-b136-abcd2f2b232c	DELIVERING	DELIVERED	9000000105	2026-07-29 18:24:14.64+00	Giao thành công
300	641f4e85-e1f2-4ae0-a15e-7d12cae5727d	\N	PENDING	9000000003	2026-07-28 11:33:00+00	Đơn được tạo
301	641f4e85-e1f2-4ae0-a15e-7d12cae5727d	PENDING	CONFIRMED	\N	2026-07-28 11:39:00+00	Shop xác nhận
302	641f4e85-e1f2-4ae0-a15e-7d12cae5727d	CONFIRMED	ASSIGNED	\N	2026-07-28 11:43:00+00	Gán shipper
303	641f4e85-e1f2-4ae0-a15e-7d12cae5727d	ASSIGNED	DELIVERING	9000000105	2026-07-28 11:54:00+00	Shipper bắt đầu giao
304	641f4e85-e1f2-4ae0-a15e-7d12cae5727d	DELIVERING	DELIVERED	9000000105	2026-07-28 12:23:11.04+00	Giao thành công
305	7bff06a1-4b9d-435f-b0ce-f5dffadfcb91	\N	PENDING	9000000003	2026-07-23 18:06:00+00	Đơn được tạo
306	7bff06a1-4b9d-435f-b0ce-f5dffadfcb91	PENDING	CONFIRMED	\N	2026-07-23 18:09:00+00	Shop xác nhận
307	7bff06a1-4b9d-435f-b0ce-f5dffadfcb91	CONFIRMED	ASSIGNED	\N	2026-07-23 18:11:00+00	Gán shipper
308	7bff06a1-4b9d-435f-b0ce-f5dffadfcb91	ASSIGNED	DELIVERING	9000000101	2026-07-23 18:24:00+00	Shipper bắt đầu giao
309	7bff06a1-4b9d-435f-b0ce-f5dffadfcb91	DELIVERING	DELIVERED	9000000101	2026-07-23 18:41:55.68+00	Giao thành công
310	f38b2ebd-8cd5-4dd4-afbb-da238f64cd82	\N	PENDING	9000001001	2026-07-12 17:28:00+00	Đơn được tạo
311	f38b2ebd-8cd5-4dd4-afbb-da238f64cd82	PENDING	CONFIRMED	\N	2026-07-12 17:37:00+00	Shop xác nhận
312	f38b2ebd-8cd5-4dd4-afbb-da238f64cd82	CONFIRMED	ASSIGNED	\N	2026-07-12 17:43:00+00	Gán shipper
313	f38b2ebd-8cd5-4dd4-afbb-da238f64cd82	ASSIGNED	DELIVERING	9000000105	2026-07-12 17:55:00+00	Shipper bắt đầu giao
314	f38b2ebd-8cd5-4dd4-afbb-da238f64cd82	DELIVERING	DELIVERED	9000000105	2026-07-12 18:50:36+00	Giao thành công
315	33d83c92-790e-4d27-bf3e-2acab5908b0b	\N	PENDING	9000001003	2026-07-26 08:09:00+00	Đơn được tạo
316	33d83c92-790e-4d27-bf3e-2acab5908b0b	PENDING	CONFIRMED	\N	2026-07-26 08:16:00+00	Shop xác nhận
317	33d83c92-790e-4d27-bf3e-2acab5908b0b	CONFIRMED	ASSIGNED	\N	2026-07-26 08:23:00+00	Gán shipper
318	33d83c92-790e-4d27-bf3e-2acab5908b0b	ASSIGNED	DELIVERING	9000000105	2026-07-26 08:35:00+00	Shipper bắt đầu giao
319	33d83c92-790e-4d27-bf3e-2acab5908b0b	DELIVERING	DELIVERED	9000000105	2026-07-26 09:28:51.12+00	Giao thành công
320	2d234eb9-1a84-4b8e-acec-a2efd1c7f3ad	\N	PENDING	9000000001	2026-07-14 09:56:00+00	Đơn được tạo
321	2d234eb9-1a84-4b8e-acec-a2efd1c7f3ad	PENDING	CONFIRMED	\N	2026-07-14 10:00:00+00	Shop xác nhận
322	2d234eb9-1a84-4b8e-acec-a2efd1c7f3ad	CONFIRMED	ASSIGNED	\N	2026-07-14 10:06:00+00	Gán shipper
323	2d234eb9-1a84-4b8e-acec-a2efd1c7f3ad	ASSIGNED	DELIVERING	9000000102	2026-07-14 10:13:00+00	Shipper bắt đầu giao
324	2d234eb9-1a84-4b8e-acec-a2efd1c7f3ad	DELIVERING	DELIVERED	9000000102	2026-07-14 10:39:54.72+00	Giao thành công
325	80df0f44-9540-40ad-b325-2d62da3f93af	\N	PENDING	9000000001	2026-07-17 14:31:00+00	Đơn được tạo
326	80df0f44-9540-40ad-b325-2d62da3f93af	PENDING	CONFIRMED	\N	2026-07-17 14:34:00+00	Shop xác nhận
327	80df0f44-9540-40ad-b325-2d62da3f93af	CONFIRMED	ASSIGNED	\N	2026-07-17 14:39:00+00	Gán shipper
328	80df0f44-9540-40ad-b325-2d62da3f93af	ASSIGNED	DELIVERING	9000000101	2026-07-17 14:51:00+00	Shipper bắt đầu giao
329	80df0f44-9540-40ad-b325-2d62da3f93af	DELIVERING	DELIVERED	9000000101	2026-07-17 15:24:17.76+00	Giao thành công
330	c18f08f0-88a4-48e2-a05d-9e0026c11360	\N	PENDING	9000001009	2026-08-08 09:20:00+00	Đơn được tạo
331	c18f08f0-88a4-48e2-a05d-9e0026c11360	PENDING	CANCELLED	\N	2026-08-08 09:46:00+00	Hết món khách chọn
332	761362f6-9711-471b-8fbc-c897ef2d0c02	\N	PENDING	9000001009	2026-07-13 18:27:00+00	Đơn được tạo
333	761362f6-9711-471b-8fbc-c897ef2d0c02	PENDING	CONFIRMED	\N	2026-07-13 18:34:00+00	Shop xác nhận
334	761362f6-9711-471b-8fbc-c897ef2d0c02	CONFIRMED	ASSIGNED	\N	2026-07-13 18:39:00+00	Gán shipper
335	761362f6-9711-471b-8fbc-c897ef2d0c02	ASSIGNED	DELIVERING	9000000104	2026-07-13 18:46:00+00	Shipper bắt đầu giao
336	761362f6-9711-471b-8fbc-c897ef2d0c02	DELIVERING	DELIVERED	9000000104	2026-07-13 19:32:47.28+00	Giao thành công
337	534f8b1a-1248-4d07-a315-0f7781edc41b	\N	PENDING	9000001005	2026-07-24 14:08:00+00	Đơn được tạo
338	534f8b1a-1248-4d07-a315-0f7781edc41b	PENDING	CONFIRMED	\N	2026-07-24 14:15:00+00	Shop xác nhận
339	534f8b1a-1248-4d07-a315-0f7781edc41b	CONFIRMED	ASSIGNED	\N	2026-07-24 14:19:00+00	Gán shipper
340	534f8b1a-1248-4d07-a315-0f7781edc41b	ASSIGNED	DELIVERING	9000000101	2026-07-24 14:29:00+00	Shipper bắt đầu giao
341	534f8b1a-1248-4d07-a315-0f7781edc41b	DELIVERING	DELIVERED	9000000101	2026-07-24 15:10:30+00	Giao thành công
342	b3868885-f520-46ba-93fc-22ee350d22d0	\N	PENDING	9000001002	2026-07-24 11:15:00+00	Đơn được tạo
343	b3868885-f520-46ba-93fc-22ee350d22d0	PENDING	CONFIRMED	\N	2026-07-24 11:26:00+00	Shop xác nhận
344	b3868885-f520-46ba-93fc-22ee350d22d0	CONFIRMED	ASSIGNED	\N	2026-07-24 11:31:00+00	Gán shipper
345	b3868885-f520-46ba-93fc-22ee350d22d0	ASSIGNED	DELIVERING	9000000101	2026-07-24 11:37:00+00	Shipper bắt đầu giao
346	b3868885-f520-46ba-93fc-22ee350d22d0	DELIVERING	DELIVERED	9000000101	2026-07-24 12:12:05.28+00	Giao thành công
347	0010521a-9d1e-4b44-9eb8-e450dc153c52	\N	PENDING	9000001007	2026-07-22 18:33:00+00	Đơn được tạo
348	0010521a-9d1e-4b44-9eb8-e450dc153c52	PENDING	CONFIRMED	\N	2026-07-22 18:36:00+00	Shop xác nhận
349	0010521a-9d1e-4b44-9eb8-e450dc153c52	CONFIRMED	ASSIGNED	\N	2026-07-22 18:40:00+00	Gán shipper
350	0010521a-9d1e-4b44-9eb8-e450dc153c52	ASSIGNED	DELIVERING	9000000102	2026-07-22 18:50:00+00	Shipper bắt đầu giao
351	0010521a-9d1e-4b44-9eb8-e450dc153c52	DELIVERING	DELIVERED	9000000102	2026-07-22 19:20:00.72+00	Giao thành công
352	798a9bb7-edac-4b3c-b7d8-a4e7b9f4bcba	\N	PENDING	9000001007	2026-08-13 06:43:35.719639+00	Đơn được tạo
353	798a9bb7-edac-4b3c-b7d8-a4e7b9f4bcba	PENDING	CONFIRMED	\N	2026-08-13 06:52:35.719639+00	Shop xác nhận
354	ca4b3cad-5556-493f-9e9d-42d73d6a0f01	\N	PENDING	9000001003	2026-08-13 06:43:35.719639+00	Đơn được tạo
355	ca4b3cad-5556-493f-9e9d-42d73d6a0f01	PENDING	CONFIRMED	\N	2026-08-13 06:50:35.719639+00	Shop xác nhận
356	261c9030-2493-4cd2-bf6d-bd69fccca4f7	\N	PENDING	9000000001	2026-08-13 05:43:35.719639+00	Đơn được tạo
357	4ca4e8e4-fd31-45f5-8828-fdf505c3ffd4	\N	PENDING	9000001010	2026-08-13 05:43:35.719639+00	Đơn được tạo
358	4ca4e8e4-fd31-45f5-8828-fdf505c3ffd4	PENDING	CONFIRMED	\N	2026-08-13 05:52:35.719639+00	Shop xác nhận
359	4ca4e8e4-fd31-45f5-8828-fdf505c3ffd4	CONFIRMED	ASSIGNED	\N	2026-08-13 05:56:35.719639+00	Gán shipper
360	4ca4e8e4-fd31-45f5-8828-fdf505c3ffd4	ASSIGNED	DELIVERING	9000000105	2026-08-13 06:07:35.719639+00	Shipper bắt đầu giao
361	4ca4e8e4-fd31-45f5-8828-fdf505c3ffd4	DELIVERING	DELIVERED	9000000105	2026-08-13 06:59:00.919639+00	Giao thành công
\.


--
-- Data for Name: telegram_user; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.telegram_user (id, username, first_name, last_name, phone, photo_url, language_code, is_blocked, created_at, updated_at, customer_rating_avg, customer_rating_count) FROM stdin;
9000000001	demo_khach_an	An	Nguyễn	0901234001	\N	vi	f	2026-08-13 07:43:35.452459+00	2026-08-13 07:43:35.452459+00	0.00	0
9000000002	demo_khach_binh	Bình	Trần	0901234002	\N	vi	f	2026-08-13 07:43:35.452459+00	2026-08-13 07:43:35.452459+00	0.00	0
9000000003	demo_khach_chi	Chi	Lê	0901234003	\N	vi	f	2026-08-13 07:43:35.452459+00	2026-08-13 07:43:35.452459+00	0.00	0
9000000101	demo_ship_dung	Dũng	Phạm	0902345101	\N	vi	f	2026-08-13 07:43:35.452459+00	2026-08-13 07:43:35.452459+00	0.00	0
9000000102	demo_ship_em	Em	Hoàng	0902345102	\N	vi	f	2026-08-13 07:43:35.452459+00	2026-08-13 07:43:35.452459+00	0.00	0
9000000103	demo_ship_phong	Phong	Đỗ	0902345103	\N	vi	f	2026-08-13 07:43:35.452459+00	2026-08-13 07:43:35.452459+00	0.00	0
9000001001	hung_tran88	Hùng	Trần	0912456001	\N	vi	f	2026-08-13 07:43:35.7044+00	2026-08-13 07:43:35.7044+00	0.00	0
9000001002	ha_pham92	Hà	Phạm	0912456002	\N	vi	f	2026-08-13 07:43:35.7044+00	2026-08-13 07:43:35.7044+00	0.00	0
9000001003	duc_le99	Đức	Lê	0912456003	\N	vi	f	2026-08-13 07:43:35.7044+00	2026-08-13 07:43:35.7044+00	0.00	0
9000001004	mai_vu95	Mai	Vũ	0912456004	\N	vi	f	2026-08-13 07:43:35.7044+00	2026-08-13 07:43:35.7044+00	0.00	0
9000001005	nam_hoang90	Nam	Hoàng	0912456005	\N	vi	f	2026-08-13 07:43:35.7044+00	2026-08-13 07:43:35.7044+00	0.00	0
9000001006	lan_do87	Lan	Đỗ	0912456006	\N	vi	f	2026-08-13 07:43:35.7044+00	2026-08-13 07:43:35.7044+00	0.00	0
9000001007	huy_bui01	Huy	Bùi	0912456007	\N	vi	f	2026-08-13 07:43:35.7044+00	2026-08-13 07:43:35.7044+00	0.00	0
9000001008	tam_ngo93	Tâm	Ngô	0912456008	\N	vi	f	2026-08-13 07:43:35.7044+00	2026-08-13 07:43:35.7044+00	0.00	0
9000001009	quynh_dang96	Quỳnh	Đặng	0912456009	\N	vi	f	2026-08-13 07:43:35.7044+00	2026-08-13 07:43:35.7044+00	0.00	0
9000001010	khanh_duong89	Khánh	Dương	0912456010	\N	vi	f	2026-08-13 07:43:35.7044+00	2026-08-13 07:43:35.7044+00	0.00	0
9000000104	ship_tuan_nv	Tuấn	Nguyễn	0902345104	\N	vi	f	2026-08-13 07:43:35.7044+00	2026-08-13 07:43:35.7044+00	0.00	0
9000000105	ship_hoa_vt	Hòa	Vũ	0902345105	\N	vi	f	2026-08-13 07:43:35.7044+00	2026-08-13 07:43:35.7044+00	0.00	0
9000000106	ship_son_dv	Sơn	Đặng	0902345106	\N	vi	f	2026-08-13 07:43:35.7044+00	2026-08-13 07:43:35.7044+00	0.00	0
\.


--
-- Data for Name: user_role; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.user_role (id, telegram_user_id, role, status, assigned_at) FROM stdin;
1	9000000001	CUSTOMER	ACTIVE	2026-08-13 07:43:35.455237+00
2	9000000002	CUSTOMER	ACTIVE	2026-08-13 07:43:35.455237+00
3	9000000003	CUSTOMER	ACTIVE	2026-08-13 07:43:35.455237+00
4	9000000101	SHIPPER	ACTIVE	2026-08-13 07:43:35.455237+00
5	9000000102	SHIPPER	ACTIVE	2026-08-13 07:43:35.455237+00
6	9000000103	SHIPPER	PENDING	2026-08-13 07:43:35.455237+00
7	9000001001	CUSTOMER	ACTIVE	2026-08-13 07:43:35.706034+00
8	9000001002	CUSTOMER	ACTIVE	2026-08-13 07:43:35.706034+00
9	9000001003	CUSTOMER	ACTIVE	2026-08-13 07:43:35.706034+00
10	9000001004	CUSTOMER	ACTIVE	2026-08-13 07:43:35.706034+00
11	9000001005	CUSTOMER	ACTIVE	2026-08-13 07:43:35.706034+00
12	9000001006	CUSTOMER	ACTIVE	2026-08-13 07:43:35.706034+00
13	9000001007	CUSTOMER	ACTIVE	2026-08-13 07:43:35.706034+00
14	9000001008	CUSTOMER	ACTIVE	2026-08-13 07:43:35.706034+00
15	9000001009	CUSTOMER	ACTIVE	2026-08-13 07:43:35.706034+00
16	9000001010	CUSTOMER	ACTIVE	2026-08-13 07:43:35.706034+00
17	9000000104	SHIPPER	ACTIVE	2026-08-13 07:43:35.708785+00
18	9000000105	SHIPPER	ACTIVE	2026-08-13 07:43:35.708785+00
19	9000000106	SHIPPER	ACTIVE	2026-08-13 07:43:35.708785+00
\.


--
-- Data for Name: voucher; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.voucher (id, code, name, target, discount_type, discount_value, max_discount, min_order_amount, valid_from, valid_until, max_uses_total, max_uses_per_customer, used_count, is_active, created_at, updated_at) FROM stdin;
1	FREESHIP	Miễn phí ship 30k	SHIPPING	FIXED	30000.00	\N	0.00	2026-08-12 07:43:35.597954+00	2026-11-11 07:43:35.597954+00	100	1	0	t	2026-08-13 07:43:35.597954+00	2026-08-13 07:43:35.597954+00
2	GIAM20K	Giảm 20 000đ cho đơn từ 100k	PRODUCTS	FIXED	20000.00	\N	100000.00	2026-08-12 07:43:35.597954+00	2026-11-11 07:43:35.597954+00	100	1	0	t	2026-08-13 07:43:35.597954+00	2026-08-13 07:43:35.597954+00
\.


--
-- Data for Name: voucher_redemption; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.voucher_redemption (id, voucher_id, order_id, customer_id, discount_applied, created_at) FROM stdin;
\.


--
-- Name: admin_user_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.admin_user_id_seq', 2, true);


--
-- Name: chat_message_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.chat_message_id_seq', 1, false);


--
-- Name: location_ping_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.location_ping_id_seq', 1, false);


--
-- Name: order_item_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.order_item_id_seq', 170, true);


--
-- Name: payment_transaction_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.payment_transaction_id_seq', 1, false);


--
-- Name: product_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.product_id_seq', 20, true);


--
-- Name: rating_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.rating_id_seq', 54, true);


--
-- Name: saved_address_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.saved_address_id_seq', 5, true);


--
-- Name: shipper_ledger_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.shipper_ledger_id_seq', 112, true);


--
-- Name: shipper_rating_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.shipper_rating_id_seq', 1, false);


--
-- Name: status_history_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.status_history_id_seq', 361, true);


--
-- Name: user_role_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.user_role_id_seq', 19, true);


--
-- Name: voucher_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.voucher_id_seq', 2, true);


--
-- Name: voucher_redemption_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.voucher_redemption_id_seq', 1, false);


--
-- Name: admin_user admin_user_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_user
    ADD CONSTRAINT admin_user_email_key UNIQUE (email);


--
-- Name: admin_user admin_user_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_user
    ADD CONSTRAINT admin_user_pkey PRIMARY KEY (id);


--
-- Name: app_meta app_meta_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_meta
    ADD CONSTRAINT app_meta_pkey PRIMARY KEY (key);


--
-- Name: chat_message chat_message_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_message
    ADD CONSTRAINT chat_message_pkey PRIMARY KEY (id);


--
-- Name: conversation_state conversation_state_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.conversation_state
    ADD CONSTRAINT conversation_state_pkey PRIMARY KEY (telegram_user_id);


--
-- Name: delivery_assignment delivery_assignment_order_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_assignment
    ADD CONSTRAINT delivery_assignment_order_id_key UNIQUE (order_id);


--
-- Name: delivery_assignment delivery_assignment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_assignment
    ADD CONSTRAINT delivery_assignment_pkey PRIMARY KEY (id);


--
-- Name: location_ping location_ping_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.location_ping
    ADD CONSTRAINT location_ping_pkey PRIMARY KEY (id);


--
-- Name: order_item order_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_item
    ADD CONSTRAINT order_item_pkey PRIMARY KEY (id);


--
-- Name: orders orders_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_code_key UNIQUE (code);


--
-- Name: orders orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_pkey PRIMARY KEY (id);


--
-- Name: payment payment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment
    ADD CONSTRAINT payment_pkey PRIMARY KEY (id);


--
-- Name: payment_transaction payment_transaction_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_transaction
    ADD CONSTRAINT payment_transaction_pkey PRIMARY KEY (id);


--
-- Name: payment payment_vnp_txn_ref_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment
    ADD CONSTRAINT payment_vnp_txn_ref_key UNIQUE (vnp_txn_ref);


--
-- Name: processed_update processed_update_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.processed_update
    ADD CONSTRAINT processed_update_pkey PRIMARY KEY (update_id);


--
-- Name: product product_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product
    ADD CONSTRAINT product_name_key UNIQUE (name);


--
-- Name: product product_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product
    ADD CONSTRAINT product_pkey PRIMARY KEY (id);


--
-- Name: rating rating_order_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rating
    ADD CONSTRAINT rating_order_id_key UNIQUE (order_id);


--
-- Name: rating rating_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rating
    ADD CONSTRAINT rating_pkey PRIMARY KEY (id);


--
-- Name: refresh_token refresh_token_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_token
    ADD CONSTRAINT refresh_token_pkey PRIMARY KEY (id);


--
-- Name: refresh_token refresh_token_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_token
    ADD CONSTRAINT refresh_token_token_hash_key UNIQUE (token_hash);


--
-- Name: saved_address saved_address_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.saved_address
    ADD CONSTRAINT saved_address_pkey PRIMARY KEY (id);


--
-- Name: shipper_ledger shipper_ledger_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shipper_ledger
    ADD CONSTRAINT shipper_ledger_pkey PRIMARY KEY (id);


--
-- Name: shipper_profile shipper_profile_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shipper_profile
    ADD CONSTRAINT shipper_profile_pkey PRIMARY KEY (user_id);


--
-- Name: shipper_rating shipper_rating_order_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shipper_rating
    ADD CONSTRAINT shipper_rating_order_id_key UNIQUE (order_id);


--
-- Name: shipper_rating shipper_rating_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shipper_rating
    ADD CONSTRAINT shipper_rating_pkey PRIMARY KEY (id);


--
-- Name: shop_config shop_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shop_config
    ADD CONSTRAINT shop_config_pkey PRIMARY KEY (id);


--
-- Name: status_history status_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.status_history
    ADD CONSTRAINT status_history_pkey PRIMARY KEY (id);


--
-- Name: telegram_user telegram_user_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.telegram_user
    ADD CONSTRAINT telegram_user_pkey PRIMARY KEY (id);


--
-- Name: saved_address uq_saved_address_coords; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.saved_address
    ADD CONSTRAINT uq_saved_address_coords UNIQUE (customer_id, lat, lng);


--
-- Name: user_role uq_user_role; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_role
    ADD CONSTRAINT uq_user_role UNIQUE (telegram_user_id, role);


--
-- Name: user_role user_role_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_role
    ADD CONSTRAINT user_role_pkey PRIMARY KEY (id);


--
-- Name: voucher voucher_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.voucher
    ADD CONSTRAINT voucher_code_key UNIQUE (code);


--
-- Name: voucher voucher_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.voucher
    ADD CONSTRAINT voucher_pkey PRIMARY KEY (id);


--
-- Name: voucher_redemption voucher_redemption_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.voucher_redemption
    ADD CONSTRAINT voucher_redemption_pkey PRIMARY KEY (id);


--
-- Name: voucher_redemption voucher_redemption_voucher_id_order_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.voucher_redemption
    ADD CONSTRAINT voucher_redemption_voucher_id_order_id_key UNIQUE (voucher_id, order_id);


--
-- Name: idx_admin_user_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_admin_user_active ON public.admin_user USING btree (is_active) WHERE (is_active = true);


--
-- Name: idx_chat_message_assignment; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_message_assignment ON public.chat_message USING btree (assignment_id, created_at);


--
-- Name: idx_conversation_state_updated_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_conversation_state_updated_at ON public.conversation_state USING btree (updated_at);


--
-- Name: idx_delivery_assignment_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_delivery_assignment_order ON public.delivery_assignment USING btree (order_id);


--
-- Name: idx_delivery_assignment_shipper; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_delivery_assignment_shipper ON public.delivery_assignment USING btree (shipper_id, status);


--
-- Name: idx_ledger_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ledger_order ON public.shipper_ledger USING btree (order_id) WHERE (order_id IS NOT NULL);


--
-- Name: idx_ledger_shipper_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ledger_shipper_date ON public.shipper_ledger USING btree (shipper_id, created_at DESC);


--
-- Name: idx_location_ping_assignment_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_location_ping_assignment_time ON public.location_ping USING btree (assignment_id, recorded_at DESC);


--
-- Name: idx_location_ping_recorded_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_location_ping_recorded_at ON public.location_ping USING btree (recorded_at);


--
-- Name: idx_order_item_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_item_order ON public.order_item USING btree (order_id);


--
-- Name: idx_orders_customer_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_customer_created ON public.orders USING btree (customer_id, created_at DESC);


--
-- Name: idx_orders_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_status_created ON public.orders USING btree (status, created_at DESC);


--
-- Name: idx_payment_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payment_order ON public.payment USING btree (order_id);


--
-- Name: idx_payment_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payment_status_created ON public.payment USING btree (status, created_at);


--
-- Name: idx_payment_tx_payment; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payment_tx_payment ON public.payment_transaction USING btree (payment_id, recorded_at);


--
-- Name: idx_processed_update_processed_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_processed_update_processed_at ON public.processed_update USING btree (processed_at);


--
-- Name: idx_product_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_product_active ON public.product USING btree (is_active) WHERE (is_active = true);


--
-- Name: idx_rating_shipper_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_rating_shipper_created ON public.rating USING btree (shipper_id, created_at DESC);


--
-- Name: idx_redemption_customer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_redemption_customer ON public.voucher_redemption USING btree (customer_id, voucher_id);


--
-- Name: idx_refresh_token_expiry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_token_expiry ON public.refresh_token USING btree (expires_at) WHERE (revoked = false);


--
-- Name: idx_refresh_token_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_token_user ON public.refresh_token USING btree (admin_user_id);


--
-- Name: idx_saved_address_customer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_saved_address_customer ON public.saved_address USING btree (customer_id, last_used_at DESC);


--
-- Name: idx_shipper_rating_customer_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shipper_rating_customer_created ON public.shipper_rating USING btree (customer_id, created_at DESC);


--
-- Name: idx_shipper_state; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shipper_state ON public.shipper_profile USING btree (current_state);


--
-- Name: idx_status_history_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_status_history_order ON public.status_history USING btree (order_id, changed_at);


--
-- Name: idx_telegram_user_username; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_telegram_user_username ON public.telegram_user USING btree (username) WHERE (username IS NOT NULL);


--
-- Name: idx_user_role_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_role_lookup ON public.user_role USING btree (telegram_user_id, status);


--
-- Name: uq_assignment_shipper_started; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_assignment_shipper_started ON public.delivery_assignment USING btree (shipper_id) WHERE ((status)::text = 'STARTED'::text);


--
-- Name: admin_user admin_user_telegram_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_user
    ADD CONSTRAINT admin_user_telegram_user_id_fkey FOREIGN KEY (telegram_user_id) REFERENCES public.telegram_user(id);


--
-- Name: chat_message chat_message_assignment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_message
    ADD CONSTRAINT chat_message_assignment_id_fkey FOREIGN KEY (assignment_id) REFERENCES public.delivery_assignment(id) ON DELETE CASCADE;


--
-- Name: conversation_state conversation_state_telegram_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.conversation_state
    ADD CONSTRAINT conversation_state_telegram_user_id_fkey FOREIGN KEY (telegram_user_id) REFERENCES public.telegram_user(id) ON DELETE CASCADE;


--
-- Name: delivery_assignment delivery_assignment_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_assignment
    ADD CONSTRAINT delivery_assignment_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE CASCADE;


--
-- Name: delivery_assignment delivery_assignment_shipper_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_assignment
    ADD CONSTRAINT delivery_assignment_shipper_id_fkey FOREIGN KEY (shipper_id) REFERENCES public.telegram_user(id);


--
-- Name: location_ping location_ping_assignment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.location_ping
    ADD CONSTRAINT location_ping_assignment_id_fkey FOREIGN KEY (assignment_id) REFERENCES public.delivery_assignment(id) ON DELETE CASCADE;


--
-- Name: order_item order_item_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_item
    ADD CONSTRAINT order_item_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE CASCADE;


--
-- Name: order_item order_item_product_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_item
    ADD CONSTRAINT order_item_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.product(id);


--
-- Name: orders orders_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.telegram_user(id);


--
-- Name: payment payment_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment
    ADD CONSTRAINT payment_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(id);


--
-- Name: payment_transaction payment_transaction_payment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_transaction
    ADD CONSTRAINT payment_transaction_payment_id_fkey FOREIGN KEY (payment_id) REFERENCES public.payment(id) ON DELETE CASCADE;


--
-- Name: rating rating_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rating
    ADD CONSTRAINT rating_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.telegram_user(id);


--
-- Name: rating rating_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rating
    ADD CONSTRAINT rating_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE CASCADE;


--
-- Name: rating rating_shipper_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rating
    ADD CONSTRAINT rating_shipper_id_fkey FOREIGN KEY (shipper_id) REFERENCES public.telegram_user(id);


--
-- Name: refresh_token refresh_token_admin_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_token
    ADD CONSTRAINT refresh_token_admin_user_id_fkey FOREIGN KEY (admin_user_id) REFERENCES public.admin_user(id) ON DELETE CASCADE;


--
-- Name: saved_address saved_address_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.saved_address
    ADD CONSTRAINT saved_address_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.telegram_user(id) ON DELETE CASCADE;


--
-- Name: shipper_ledger shipper_ledger_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shipper_ledger
    ADD CONSTRAINT shipper_ledger_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE RESTRICT;


--
-- Name: shipper_ledger shipper_ledger_shipper_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shipper_ledger
    ADD CONSTRAINT shipper_ledger_shipper_id_fkey FOREIGN KEY (shipper_id) REFERENCES public.shipper_profile(user_id) ON DELETE RESTRICT;


--
-- Name: shipper_profile shipper_profile_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shipper_profile
    ADD CONSTRAINT shipper_profile_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.telegram_user(id) ON DELETE CASCADE;


--
-- Name: shipper_rating shipper_rating_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shipper_rating
    ADD CONSTRAINT shipper_rating_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.telegram_user(id);


--
-- Name: shipper_rating shipper_rating_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shipper_rating
    ADD CONSTRAINT shipper_rating_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE CASCADE;


--
-- Name: shipper_rating shipper_rating_shipper_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shipper_rating
    ADD CONSTRAINT shipper_rating_shipper_id_fkey FOREIGN KEY (shipper_id) REFERENCES public.telegram_user(id);


--
-- Name: status_history status_history_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.status_history
    ADD CONSTRAINT status_history_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE CASCADE;


--
-- Name: user_role user_role_telegram_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_role
    ADD CONSTRAINT user_role_telegram_user_id_fkey FOREIGN KEY (telegram_user_id) REFERENCES public.telegram_user(id) ON DELETE CASCADE;


--
-- Name: voucher_redemption voucher_redemption_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.voucher_redemption
    ADD CONSTRAINT voucher_redemption_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.telegram_user(id);


--
-- Name: voucher_redemption voucher_redemption_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.voucher_redemption
    ADD CONSTRAINT voucher_redemption_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(id);


--
-- Name: voucher_redemption voucher_redemption_voucher_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.voucher_redemption
    ADD CONSTRAINT voucher_redemption_voucher_id_fkey FOREIGN KEY (voucher_id) REFERENCES public.voucher(id) ON DELETE RESTRICT;


--
-- PostgreSQL database dump complete
--

\unrestrict 5QgQKibEke5SagZlgoesgRo9BfNvBVL1L2yqxXTW9ZQYxLvSwNbFWSye035ILrR

