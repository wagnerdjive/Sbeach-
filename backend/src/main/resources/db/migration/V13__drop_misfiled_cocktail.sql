-- The previous site listed the non-alcoholic cocktail "Vanilla Fresh" under beers as well; it already appears under "Cocktails Sem Álcool".
DELETE FROM menu_items WHERE section_pt = 'Cervejas' AND name_pt = 'Vanilla Fresh';
