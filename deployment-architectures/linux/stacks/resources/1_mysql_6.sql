CREATE TABLE IF NOT EXISTS users (
      user_id BIGINT NOT NULL,
      tenant_id VARCHAR(10) NULL,
      password VARCHAR(512) NULL,
      salt VARCHAR(128) NULL,
      hash_algorithm_id INT NULL,
      enabled BOOLEAN NOT NULL,

      UNIQUE (user_id)
);

ALTER TABLE users
ADD CONSTRAINT pk_users PRIMARY KEY (user_id);

CREATE TABLE IF NOT EXISTS hash_algorithm(
	  id INT AUTO_INCREMENT PRIMARY KEY,
	  algorithm VARCHAR(100) NOT NULL,
	  properties VARCHAR(512) NULL,
	  is_default BOOLEAN NULL
);

CREATE TABLE IF NOT EXISTS user_info (
	user_id BIGINT NOT NULL,
	name VARCHAR(256) NULL,
	username VARCHAR(50) NULL,
	tenant_id VARCHAR(10) NULL,
	given_name VARCHAR(256) NULL,
	family_name VARCHAR(256) NULL,
	middle_name VARCHAR(256) NULL,
	title VARCHAR(10) NULL,
	sub VARCHAR(2014) NOT NULL,
	nickname VARCHAR(256) NULL,
	profile VARCHAR(256) NULL,
	picture VARCHAR(256) NULL,
	website VARCHAR(256) NULL,
	email VARCHAR(256) NULL,
	email_verified BOOLEAN NULL,
	gender VARCHAR(256) NULL,
	zoneinfo VARCHAR(256) NULL,
	locale VARCHAR(256) NULL,
	phone_number VARCHAR(256) NULL,
	phone_number_verified BOOLEAN NULL,
	address_id VARCHAR(256) NULL,
	updated_at TIMESTAMP,
	birthdate DATE NULL,
	src VARCHAR(4096) NULL,
	account_type varchar(50) NULL,
	external_reference_1 VARCHAR(256) NULL,
	external_reference_2 VARCHAR(256) NULL,
	external_reference_3 VARCHAR(256) NULL,
	supplementary_data VARCHAR(4096) NULL,
	first_sign_on_at TIMESTAMP NULL,
	welcome_message_sent_at TIMESTAMP NULL,

	UNIQUE (username)
);

ALTER TABLE user_info
ADD CONSTRAINT user_info_unique_user_id UNIQUE(user_id);

CREATE TABLE IF NOT EXISTS address (
	id BIGINT AUTO_INCREMENT PRIMARY KEY,
	formatted VARCHAR(2048),
	street_address VARCHAR(256),
	locality VARCHAR(256),
	region VARCHAR(256),
	postal_code VARCHAR(256),
	country VARCHAR(256)
);

CREATE TABLE IF NOT EXISTS client_details (
	id BIGINT AUTO_INCREMENT PRIMARY KEY,
    
	client_id VARCHAR(256) NOT NULL,
	client_secret VARCHAR(2048) NOT NULL,
	tenant_id VARCHAR(10) NULL,
	access_token_validity_seconds BIGINT,
	refresh_token_validity_seconds BIGINT,

	client_description VARCHAR(1024),
	reuse_refresh_tokens BOOLEAN DEFAULT true NOT NULL,
	dynamically_registered BOOLEAN DEFAULT false NOT NULL,
	allow_introspection BOOLEAN DEFAULT false NOT NULL,
	id_token_validity_seconds BIGINT,
	device_code_validity_seconds BIGINT,
	
	application_type VARCHAR(256),
	client_name VARCHAR(256),
	token_endpoint_auth_method VARCHAR(256),
	subject_type VARCHAR(256),
	redirect_uris VARCHAR(2048),
	
	logo_uri VARCHAR(2048),
	policy_uri VARCHAR(2048),
	client_uri VARCHAR(2048),
	tos_uri VARCHAR(2048),

	jwks_uri VARCHAR(2048),
	jwks VARCHAR(8192),
	sector_identifier_uri VARCHAR(2048),
	
	request_object_signing_alg VARCHAR(256),
	
	user_info_signed_response_alg VARCHAR(256),
	user_info_encrypted_response_alg VARCHAR(256),
	user_info_encrypted_response_enc VARCHAR(256),
	
	id_token_signed_response_alg VARCHAR(256),
	id_token_encrypted_response_alg VARCHAR(256),
	id_token_encrypted_response_enc VARCHAR(256),
	
	token_endpoint_auth_signing_alg VARCHAR(256),
	
	default_max_age BIGINT,
	require_auth_time BOOLEAN,
	created_at TIMESTAMP NULL,
	initiate_login_uri VARCHAR(2048),
	clear_access_tokens_on_refresh BOOLEAN DEFAULT true NOT NULL,
	
	software_statement VARCHAR(4096),
	software_id VARCHAR(2048),
	software_version VARCHAR(2048),
	
	code_challenge_method VARCHAR(256),
	
	UNIQUE (client_id)
);

CREATE TABLE IF NOT EXISTS authorization_code (
	id BIGINT AUTO_INCREMENT PRIMARY KEY,
	authentication_request_id BIGINT NOT NULL,
	client_id VARCHAR(256) NOT NULL,
	user_id BIGINT NOT NULL,
	code VARCHAR(4096) NOT NULL,
	expiration TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS access_token (
	id BIGINT AUTO_INCREMENT PRIMARY KEY,
	client_id VARCHAR(256) NOT NULL,
	user_id BIGINT NOT NULL,
	access_token VARCHAR(4096) NOT NULL,
	id_token VARCHAR(4096) NULL,
	expiration TIMESTAMP NULL,
	token_type VARCHAR(256),
	refresh_token_id BIGINT NULL,
	scope VARCHAR(2048) NULL,
	authorization_code_id BIGINT NULL
);

CREATE TABLE IF NOT EXISTS refresh_token (
	id BIGINT AUTO_INCREMENT PRIMARY KEY,
	client_id VARCHAR(256) NOT NULL,
	user_id BIGINT NOT NULL,
	refresh_token VARCHAR(4096) NOT NULL,
	expiration TIMESTAMP NULL,
	scope VARCHAR(2048) NULL
);

CREATE TABLE IF NOT EXISTS federated_authorization_code (
	client_id VARCHAR(256) NOT NULL,
    user_id BIGINT NOT NULL,
	authentication_request_id BIGINT NOT NULL,
    provider VARCHAR(25) NOT NULL,
	code VARCHAR(4096) NOT NULL,
	expiration TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS federated_access_token (
	client_id VARCHAR(256) NOT NULL,
    user_id BIGINT NOT NULL,
    provider VARCHAR(25) NOT NULL,
	access_token VARCHAR(4096) NOT NULL,
    id_token VARCHAR(4096) NULL,
	expiration TIMESTAMP NULL,
	token_type VARCHAR(256),
	refresh_token_id BIGINT NULL
);

CREATE TABLE IF NOT EXISTS federated_refresh_token (
	id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
	client_id VARCHAR(256) NOT NULL,
    provider VARCHAR(25) NOT NULL,
	refresh_token VARCHAR(4096) NOT NULL,
	expiration TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS authentication_request (
	id BIGINT AUTO_INCREMENT PRIMARY KEY,
	client_id VARCHAR(256) NOT NULL,
	redirect_uri VARCHAR(2048) NOT NULL,
	response_type VARCHAR(2048) NOT NULL,
	scope VARCHAR(2048) NULL,
	state VARCHAR(2048) NULL
);

CREATE TABLE IF NOT EXISTS authentication_request_parameter (
	authentication_request_id BIGINT NOT NULL,
	param VARCHAR(2048) NOT NULL,
	val VARCHAR(2048) NULL
);

CREATE TABLE IF NOT EXISTS password_reset_token (
	id VARCHAR(256) NOT NULL,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(256) NOT NULL,
    salt VARCHAR(128) NOT NULL,
	created_at TIMESTAMP NOT NULL,
	tenant_id VARCHAR(10) NULL
);

ALTER TABLE password_reset_token
ADD CONSTRAINT password_reset_token_unique_id UNIQUE(id);

CREATE TABLE IF NOT EXISTS request_data (
	id VARCHAR(256) NOT NULL UNIQUE,
	data VARCHAR(4096) NOT NULL
);

CREATE TABLE IF NOT EXISTS tenant(
	  tenant_id VARCHAR(10) NOT NULL PRIMARY KEY,
      tenant_name VARCHAR(30) NOT NULL,
      tenant_owner_id BIGINT NULL,
      username_attribute VARCHAR(30) NULL,
      hash_algorithm_id INT NULL,
      external_reference_1_name VARCHAR(256) NULL,
      external_reference_2_name VARCHAR(256) NULL,
      external_reference_3_name VARCHAR(256) NULL,
      html_location VARCHAR(256) NULL,
      reset_token_validity_seconds BIGINT NULL,
      default_redirect_location VARCHAR(2048) NULL,
      user_outbound_service_location VARCHAR(2048) NULL,
      css_location VARCHAR(2048) NULL,

      UNIQUE(tenant_name)
);

CREATE TABLE IF NOT EXISTS role (
	id BIGINT AUTO_INCREMENT PRIMARY KEY,
	role_name VARCHAR(50) NOT NULL,
	tenant_id VARCHAR(10) NOT NULL
);

CREATE TABLE IF NOT EXISTS permission (
	id BIGINT AUTO_INCREMENT PRIMARY KEY,
	permission_name VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS role_permission (
	role_id BIGINT NOT NULL,
	permission_id BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS user_role (
	user_id BIGINT NOT NULL,
	role_id BIGINT NOT NULL
);

CREATE TABLE number_sequence (
      sequence_name VARCHAR(50) NOT NULL,
      tenant_id VARCHAR(10) NOT NULL,
      min_value BIGINT NULL,
      max_value BIGINT NULL,
      value BIGINT NOT NULL DEFAULT 0
);

ALTER TABLE number_sequence
ADD CONSTRAINT unique_sequence UNIQUE (sequence_name, tenant_id);

CREATE TABLE IF NOT EXISTS  licences (
  	id BIGINT AUTO_INCREMENT PRIMARY KEY,
  	valid_from datetime NOT NULL,
  	valid_to datetime NOT NULL,
  	xml NVARCHAR(20000) NOT NULL
);

CREATE TABLE config_properties (
      name VARCHAR(50) NOT NULL,
      value VARCHAR(500) NOT NULL,
      tenant_id VARCHAR(10) NULL
);

CREATE TABLE audit_log (
      user_id BIGINT NOT NULL,
      client_id VARCHAR(256) NULL,
      activity_date_time TIMESTAMP NOT NULL,
      action VARCHAR(128) NOT NULL,
      old_data TEXT NULL,
      new_data TEXT NULL
);

-- Creating indexes

CREATE INDEX refresh_token_client_id
ON refresh_token (client_id);

CREATE INDEX refresh_token_refresh_token
ON refresh_token (refresh_token(10));

CREATE INDEX access_token_client_id
ON access_token (client_id);

CREATE INDEX access_token_access_token
ON access_token (access_token(10));

CREATE INDEX access_token_authorization_code_id
ON access_token (authorization_code_id);

CREATE INDEX authorization_code_authentication_request_id
ON authorization_code (authentication_request_id);

CREATE INDEX authorization_code_code
ON authorization_code (code(10));

CREATE INDEX authorization_client_id
ON authorization_code (client_id);

CREATE INDEX client_details_client_name
ON client_details (client_name);

CREATE INDEX user_info_email
ON user_info (email);

CREATE INDEX user_info_birthdate
ON user_info (birthdate);

CREATE INDEX role_tenant_id
ON role (tenant_id);

CREATE INDEX permission_permission_name
ON permission (permission_name);
