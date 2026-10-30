-- 模板种子数据：覆盖 4 个分类、1~3 张素材要求、不同大小限制
-- 封面为自绘 SVG，位于 backend/src/main/resources/static/media/covers/，由 Spring Boot 静态资源直接对外提供
INSERT INTO template (id, title, category, cover_url, description, required_image_count, accepted_mime_types, max_file_size_bytes, sort_order) VALUES
('travel-postcard', '旅行明信片', '创意图片', '/media/covers/travel-postcard.svg',
 '上传两张旅途照片，拼成一张带边框与地名的明信片。适合风景与合影各一张。', 2, 'image/jpeg,image/png', 5242880, 10),

('film-portrait', '胶片人像', '创意图片', '/media/covers/film-portrait.svg',
 '一张人像照片，混入胶片颗粒与暗角，输出竖版人像卡。', 1, 'image/jpeg,image/png', 8388608, 20),

('poster-bold', '活动海报', '品牌海报', '/media/covers/poster-bold.svg',
 '用一张主图生成大字号活动海报，自动留出标题区与页脚信息区。', 1, 'image/jpeg,image/png', 8388608, 30),

('social-cover', '社交封面', '社交封面', '/media/covers/social-cover.svg',
 '把照片裁成 16:9 封面，叠加底部信息条，适合主页与频道头图。', 1, 'image/jpeg,image/png', 5242880, 40),

('product-card', '商品卡片', '电商素材', '/media/covers/product-card.svg',
 '上传三张商品图，生成带价格区与按钮位的商品卡，第一张作为主图。', 3, 'image/jpeg,image/png', 5242880, 50),

('avatar-frame', '头像边框', '社交封面', '/media/covers/avatar-frame.svg',
 '一张头像照片，套上圆形描边外框，输出方版头像图。', 1, 'image/jpeg,image/png,image/webp', 3145728, 60);