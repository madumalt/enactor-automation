
-- Insert Admin User

INSERT INTO users (user_id, password, salt, enabled) 
	VALUES (1, 
	 		'd63dae218ada3bc856962cb4257971ad06b1f87f9ebe1ad9', 
	 		'ec4301b724cf4168',
	 		1);

INSERT INTO user_info (user_id, username, sub) VALUES (1, 'admin', '1');

-- Inserting Dummy Data for Testing
INSERT INTO users (user_id, password, salt, enabled) 
	VALUES (2, 
	 		'848abdceae30234cfa35f8198e222914378cf78eb9c172fc', 
	 		'7b1341df008f21a5',
	 		1);

INSERT INTO user_info (user_id, username, name, sub) VALUES (2, 'testuser', 'Test User', 2);

INSERT INTO client_details (client_name, client_id, client_secret) VALUES ('Test Client', 'client', 'secret');

INSERT INTO role
	(role_name, tenant_id)
VALUES 
	('admin', 'global');
	
INSERT INTO permission
	(permission_name)
VALUES 
	('manage_users'),
	('manage_clients'),
	('manage_tenants'),
	('manage_server');
	
INSERT INTO role_permission
	(role_id, permission_id)
VALUES 
	(1, 1),
	(1,2),
	(1,3),
	(2,1),
	(2,2),
	(3,1),
	(1, 4);

INSERT INTO user_role
	(user_id, role_id)
VALUES 
	(1, 1),
	(1,2),
	(1,3);

-- Here value should be greater than the max value of user-id in EM users.
INSERT INTO number_sequence
	(sequence_name,tenant_id,min_value,max_value,value)
VALUES
	('userIdSequence','global',1,null,1000);
	
INSERT INTO config_properties (name,value,tenant_id) VALUES ('twitter.auth.endpoint','https://api.twitter.com/oauth/authorize',null);
INSERT INTO config_properties (name,value,tenant_id) VALUES ('twitter.token.endpoint','https://api.twitter.com/oauth/access_token',null);
INSERT INTO config_properties (name,value,tenant_id) VALUES ('fb.auth.endpoint','https://www.facebook.com/v2.10/dialog/oauth',null);
INSERT INTO config_properties (name,value,tenant_id) VALUES ('fb.response.type','code%20token',null);
INSERT INTO config_properties (name,value,tenant_id) VALUES ('fb.scope','public_profile email',null);
INSERT INTO config_properties (name,value,tenant_id) VALUES ('fb.token.endpoint','https://graph.facebook.com/v2.10/oauth/access_token',null);
INSERT INTO config_properties (name,value,tenant_id) VALUES ('google.auth.endpoint','https://accounts.google.com/o/oauth2/v2/auth',null);
INSERT INTO config_properties (name,value,tenant_id) VALUES ('google.scope','profile email',null);
INSERT INTO config_properties (name,value,tenant_id) VALUES ('google.token.endpoint','https://www.googleapis.com/oauth2/v4/token',null);
INSERT INTO config_properties (name,value,tenant_id) VALUES ('default.redirect.url','welcome.html',null);
INSERT INTO config_properties (name,value,tenant_id) VALUES ('email.sender.classname','com.enactor.identity.provider.integration.smtp.BasicSMTPEmailSender',null);
INSERT INTO config_properties (name,value,tenant_id) VALUES ('dotmailer.triggered.campaign.endpoint','https://r1-api.dotmailer.com/v2/email/triggered-campaign',null);
INSERT INTO config_properties (name, value) VALUES ('email.sender.classname', 'com.enactor.identity.provider.integration.smtp.BasicSMTPEmailSender');


INSERT INTO config_properties (name, value) VALUES ('integration.smtp.host', 'localhost');
INSERT INTO config_properties (name, value) VALUES ('integration.smtp.port', '2525');
INSERT INTO config_properties (name, value) VALUES ('integration.smtp.username', 'admin');
INSERT INTO config_properties (name, value) VALUES ('integration.smtp.password', 'admin');
INSERT INTO config_properties (name, value) VALUES ('integration.smtp.default.sender', 'identity.server@enactor.co.uk');
INSERT INTO config_properties (name, value) VALUES ('integration.smtp.ssl.on.connect', 'false');

INSERT INTO config_properties (name, value) VALUES ('google.client.id', '301686143261-ui4ajl9pvna9k10tfgot97frbjmb2ue6.apps.googleusercontent.com');
INSERT INTO config_properties (name, value) VALUES ('google.client.secret', 'VK16WmUaPNvr4RPUQ0gtCOoO');
INSERT INTO config_properties (name, value) VALUES ('fb.client.id', '1808244989486842');
INSERT INTO config_properties (name, value) VALUES ('fb.client.secret', 'ac951563781cd1c0480bd0586927e900');
INSERT INTO config_properties (name, value) VALUES ('twitter.client.id', 'bp1MwxDiLTQdZwLpwhDxqGzfp');
INSERT INTO config_properties (name, value) VALUES ('twitter.client.secret', '8s7S8Xa3bVGac3rKfO89SZquQrln5seAnk4LGkdumAffAX6Bxd');

INSERT INTO config_properties (name, value) VALUES ('id.token.issuer', 'http://www.enactor.co/');

INSERT INTO hash_algorithm
	(algorithm, is_default)
VALUES
	('Raw Bcrypt', 0);
	
INSERT INTO hash_algorithm
	(algorithm, is_default)
VALUES	
	('OpenBSD Bcrypt', 1);

UPDATE users
SET hash_algorithm_id = (SELECT id from hash_algorithm WHERE algorithm = 'Raw Bcrypt');

INSERT INTO config_properties (name,value) VALUES ('auditlog.enabled','true');
INSERT INTO config_properties (name,value) VALUES ('auditlog.login','true');
INSERT INTO config_properties (name,value) VALUES ('auditlog.failed_login','true');
INSERT INTO config_properties (name,value) VALUES ('auditlog.registration','true');
INSERT INTO config_properties (name,value) VALUES ('auditlog.password_changed','true');
INSERT INTO config_properties (name,value) VALUES ('auditlog.password_forgotten','true');
