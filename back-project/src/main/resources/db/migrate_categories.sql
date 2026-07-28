-- Migration: add species to knowledge category
ALTER TABLE t_knowledge_category ADD COLUMN IF NOT EXISTS species TINYINT DEFAULT NULL COMMENT '1=猫 2=狗';

-- Update existing categories to cats
UPDATE t_knowledge_category SET species = 1 WHERE species IS NULL;

-- Add sub-categories for dogs
INSERT INTO t_knowledge_category (name, species, sort_order) VALUES
('叫声', 2, 1), ('肢体动作', 2, 2), ('病症', 2, 3), ('其他', 2, 4);

-- Update existing cats sub-categories  
UPDATE t_knowledge_category SET name = '叫声' WHERE id = 1 AND species = 1;
UPDATE t_knowledge_category SET name = '肢体动作' WHERE id = 2 AND species = 1;
UPDATE t_knowledge_category SET name = '病症' WHERE id = 3 AND species = 1;
UPDATE t_knowledge_category SET name = '其他' WHERE id = 4 AND species = 1;

-- Remove old extra categories (5,6) if they don't fit the new structure
DELETE FROM t_knowledge_category WHERE id IN (5, 6);
