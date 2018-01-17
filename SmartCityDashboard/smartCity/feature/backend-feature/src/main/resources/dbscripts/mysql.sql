CREATE TABLE IF NOT EXISTS `places` (
  `placeId` INTEGER NOT NULL AUTO_INCREMENT,
  `placeName` VARCHAR(100) NULL DEFAULT NULL,
  `lng` VARCHAR(100) NULL DEFAULT NULL,
  `lat` VARCHAR(100) NULL DEFAULT NULL,
  `image` BLOB NULL DEFAULT NULL,
  PRIMARY KEY (`placeId`) )
  ENGINE = InnoDB;

CREATE  TABLE IF NOT EXISTS `placeDevices` (
  `deviceId` VARCHAR(45) NOT NULL ,
  `placeId` VARCHAR(100) NULL DEFAULT NULL,
  `deviceType` VARCHAR(100) NULL DEFAULT NULL,
  `xCoordinate` VARCHAR(100) NULL DEFAULT NULL,
  `yCoordinate` VARCHAR(100) NULL DEFAULT NULL,
  `lastKnown` BIGINT,
  PRIMARY KEY (`deviceId`,`placeId`),
  FOREIGN KEY (`placeId`) REFERENCES places(`placeId`))
  ENGINE = InnoDB;




