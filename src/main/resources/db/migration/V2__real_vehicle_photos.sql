-- Replace the generic stock photos on the demo fleet with real photos of each model
-- (served by the app from /vehicles/, credits at /vehicles/credits.html).
-- Only rows still using one of the old placeholder images (or none) are changed, so photos that
-- staff set themselves are kept.

UPDATE vehicles v
SET image_url = p.url
FROM (VALUES
    ('toyota rav4',     '/vehicles/toyota-rav4.jpg'),
    ('toyota prado',    '/vehicles/toyota-prado.jpg'),
    ('hyundai tucson',  '/vehicles/hyundai-tucson.jpg'),
    ('toyota corolla',  '/vehicles/toyota-corolla.jpg'),
    ('nissan x-trail',  '/vehicles/nissan-x-trail.jpg'),
    ('ford ranger',     '/vehicles/ford-ranger.jpg'),
    ('toyota hiace',    '/vehicles/toyota-hiace.jpg'),
    ('toyota vitz',     '/vehicles/toyota-vitz.jpg')
) AS p(model, url)
WHERE lower(v.model) = p.model
  AND (v.image_url IS NULL
       OR v.image_url = ''
       OR v.image_url LIKE 'https://images.unsplash.com/photo-1783557105881-%'
       OR v.image_url LIKE 'https://images.unsplash.com/photo-1682773083896-%'
       OR v.image_url LIKE 'https://images.unsplash.com/photo-1773423203025-%'
       OR v.image_url LIKE 'https://images.unsplash.com/photo-1786702885812-%');
