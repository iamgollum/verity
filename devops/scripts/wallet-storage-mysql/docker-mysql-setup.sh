#!/usr/bin/env bash
set -e
MYSQL_ENV_MYSQL_ROOT_PASSWORD=root
MYSQL_ENV_MYSQL_ROOT_USERNAME=root
MYSQL_PORT_3306_TCP_PORT=3306
MYSQL_PORT_3306_TCP_ADDR=127.0.0.1

mysql -u"$MYSQL_ENV_MYSQL_ROOT_USERNAME" -p"$MYSQL_ENV_MYSQL_ROOT_PASSWORD" -h"$MYSQL_PORT_3306_TCP_ADDR" -P"$MYSQL_PORT_3306_TCP_PORT"  -e "CREATE DATABASE IF NOT EXISTS wallet DEFAULT CHARACTER SET latin1;"

mysql -u"$MYSQL_ENV_MYSQL_ROOT_USERNAME" -p"$MYSQL_ENV_MYSQL_ROOT_PASSWORD" -h"$MYSQL_PORT_3306_TCP_ADDR" -P"$MYSQL_PORT_3306_TCP_PORT"  -D wallet -e "drop TABLE if exists items;"

mysql -u"$MYSQL_ENV_MYSQL_ROOT_USERNAME" -p"$MYSQL_ENV_MYSQL_ROOT_PASSWORD" -h"$MYSQL_PORT_3306_TCP_ADDR" -P"$MYSQL_PORT_3306_TCP_PORT"  -D wallet -e "drop TABLE if exists wallets;"

mysql -u"$MYSQL_ENV_MYSQL_ROOT_USERNAME" -p"$MYSQL_ENV_MYSQL_ROOT_PASSWORD" -h"$MYSQL_PORT_3306_TCP_ADDR" -P"$MYSQL_PORT_3306_TCP_PORT"  -D wallet -e "CREATE TABLE IF NOT EXISTS wallets (
	    id BIGINT(20) NOT NULL AUTO_INCREMENT,
	    name VARCHAR(1024) NOT NULL,
	    metadata VARCHAR(10240) NOT NULL,
	    PRIMARY KEY (id),
	    UNIQUE KEY wallet_name (name)
	) ENGINE=InnoDB DEFAULT CHARSET=latin1;"

mysql -u"$MYSQL_ENV_MYSQL_ROOT_USERNAME" -p"$MYSQL_ENV_MYSQL_ROOT_PASSWORD" -h"$MYSQL_PORT_3306_TCP_ADDR" -P"$MYSQL_PORT_3306_TCP_PORT"  -D wallet -e "CREATE TABLE IF NOT EXISTS items (
	    id BIGINT(20) NOT NULL AUTO_INCREMENT,
	    wallet_id BIGINT(20) NOT NULL,
	    type VARCHAR(128) NOT NULL,
	    name VARCHAR(1024) NOT NULL,
	    value LONGBLOB NOT NULL,
	    tags JSON NOT NULL,
	    PRIMARY KEY (id),
	    UNIQUE KEY ux_items_wallet_id_type_name (wallet_id, type, name),
	    CONSTRAINT fk_items_wallet_id FOREIGN KEY (wallet_id)
		REFERENCES wallets (id)
		ON DELETE CASCADE
		ON UPDATE CASCADE
	) ENGINE=InnoDB DEFAULT CHARSET=latin1;"


mysql -u"$MYSQL_ENV_MYSQL_ROOT_USERNAME" -p"$MYSQL_ENV_MYSQL_ROOT_PASSWORD" -h"$MYSQL_PORT_3306_TCP_ADDR" -P"$MYSQL_PORT_3306_TCP_PORT"  -e "CREATE USER msuser@'%' IDENTIFIED BY 'mspassword';"
mysql -u"$MYSQL_ENV_MYSQL_ROOT_USERNAME" -p"$MYSQL_ENV_MYSQL_ROOT_PASSWORD" -h"$MYSQL_PORT_3306_TCP_ADDR" -P"$MYSQL_PORT_3306_TCP_PORT"  -e "GRANT ALL ON *.* TO 'msuser'@'%';"
mysql -u"$MYSQL_ENV_MYSQL_ROOT_USERNAME" -p"$MYSQL_ENV_MYSQL_ROOT_PASSWORD" -h"$MYSQL_PORT_3306_TCP_ADDR" -P"$MYSQL_PORT_3306_TCP_PORT"  -e "FLUSH PRIVILEGES;"

echo "complete"
