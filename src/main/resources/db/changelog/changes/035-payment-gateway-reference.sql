--liquibase formatted sql

--changeset ektrepha:35
-- Razorpay is now wired up (see com.ektrepha.payment) - provider_reference holds the Razorpay order
-- id (set at initiatePayment, looked up by RazorpayWebhookController), and this new column holds the
-- actual charge id (razorpay_payment_id) once a payment is verified/settled.

ALTER TABLE payment_transaction ADD COLUMN gateway_payment_id VARCHAR(100);
COMMENT ON COLUMN payment_transaction.provider_reference IS 'Razorpay order id, set when the order is created at initiatePayment';
COMMENT ON COLUMN payment_transaction.gateway_payment_id IS 'Razorpay payment id (razorpay_payment_id), set once the payment is verified/settled';
