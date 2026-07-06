ALTER TABLE ops_issue
  ADD COLUMN building_name VARCHAR(100) NULL COMMENT '建筑/楼栋',
  ADD COLUMN floor_name VARCHAR(100) NULL COMMENT '楼层',
  ADD COLUMN area_name VARCHAR(100) NULL COMMENT '区域/房间',
  ADD COLUMN system_type VARCHAR(100) NULL COMMENT '系统/设备类型',
  ADD COLUMN device_point VARCHAR(100) NULL COMMENT '设备编号/点位',
  ADD COLUMN internal_conclusion TEXT NULL COMMENT '内部处理结论',
  ADD COLUMN customer_feedback TEXT NULL COMMENT '客户反馈口径';
