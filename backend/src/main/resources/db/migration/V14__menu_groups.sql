-- Groups inside each menu (the tabs of the previous site: À la carte, Sushi, Tapas, Bebidas, Cocktails), so long menus can be browsed group by group.
ALTER TABLE menu_items ADD COLUMN group_pt VARCHAR(60);
ALTER TABLE menu_items ADD COLUMN group_en VARCHAR(60);
UPDATE menu_items SET group_pt = 'À la carte', group_en = 'À la carte' WHERE venue = 'RESTAURANT' AND position BETWEEN 1 AND 92;
UPDATE menu_items SET group_pt = 'Tapas', group_en = 'Tapas' WHERE venue = 'BEACH' AND position BETWEEN 93 AND 159;
UPDATE menu_items SET group_pt = 'Pratos e petiscos', group_en = 'Dishes and snacks' WHERE venue = 'SPORTS' AND position BETWEEN 160 AND 199;
UPDATE menu_items SET group_pt = 'Sushi', group_en = 'Sushi' WHERE venue = 'RESTAURANT' AND position BETWEEN 201 AND 257;
UPDATE menu_items SET group_pt = 'Tapas', group_en = 'Tapas' WHERE venue = 'RESTAURANT' AND position BETWEEN 258 AND 324;
UPDATE menu_items SET group_pt = 'Bebidas', group_en = 'Drinks' WHERE venue = 'RESTAURANT' AND position BETWEEN 325 AND 433;
UPDATE menu_items SET group_pt = 'Cocktails', group_en = 'Cocktails' WHERE venue = 'RESTAURANT' AND position BETWEEN 434 AND 490;
UPDATE menu_items SET group_pt = 'Sushi', group_en = 'Sushi' WHERE venue = 'BEACH' AND position BETWEEN 491 AND 547;
UPDATE menu_items SET group_pt = 'Bebidas', group_en = 'Drinks' WHERE venue = 'BEACH' AND position BETWEEN 548 AND 656;
UPDATE menu_items SET group_pt = 'Cocktails', group_en = 'Cocktails' WHERE venue = 'BEACH' AND position BETWEEN 657 AND 713;
UPDATE menu_items SET group_pt = 'Bebidas', group_en = 'Drinks' WHERE venue = 'SPORTS' AND position BETWEEN 714 AND 822;
UPDATE menu_items SET group_pt = 'Cocktails', group_en = 'Cocktails' WHERE venue = 'SPORTS' AND position BETWEEN 823 AND 879;
