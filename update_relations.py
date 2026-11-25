import os
import re

entity_dir = r'e:\Project\zelvira\zelvira-backend\src\main\java\com\zelvira\zelvira\entity'

replacements = {
    'Address.java': (r'(private Long userId;)', r'\1\n\n    @ManyToOne(fetch = FetchType.LAZY)\n    @JoinColumn(name = "user_id", insertable = false, updatable = false)\n    private User user;'),
    'ContentItem.java': (r'(private Long productId;)', r'\1\n\n    @ManyToOne(fetch = FetchType.LAZY)\n    @JoinColumn(name = "product_id", insertable = false, updatable = false)\n    private Product product;'),
    'DigiflazzTransaction.java': (r'(private Long userId;)', r'\1\n\n    @ManyToOne(fetch = FetchType.LAZY)\n    @JoinColumn(name = "user_id", insertable = false, updatable = false)\n    private User user;'),
    'NotificationHistory.java': (r'(private Long userId; // Null if global/news)', r'\1\n\n    @ManyToOne(fetch = FetchType.LAZY)\n    @JoinColumn(name = "user_id", insertable = false, updatable = false)\n    private User user;'),
    'Order.java': (r'(private Long userId;)', r'\1\n\n    @ManyToOne(fetch = FetchType.LAZY)\n    @JoinColumn(name = "user_id", insertable = false, updatable = false)\n    private User user;'),
    'OrderItem.java': (r'(private Long productId;)', r'\1\n\n    @ManyToOne(fetch = FetchType.LAZY)\n    @JoinColumn(name = "product_id", insertable = false, updatable = false)\n    private Product product;'),
    'ProductImage.java': (r'(private Long variantId;)', r'\1\n\n    @ManyToOne(fetch = FetchType.LAZY)\n    @JoinColumn(name = "variant_id", insertable = false, updatable = false)\n    private ProductVariant variant;'),
    'Review.java': (r'(private Long userId;)', r'\1\n\n    @ManyToOne(fetch = FetchType.LAZY)\n    @JoinColumn(name = "user_id", insertable = false, updatable = false)\n    private User user;'),
    'WalletTransaction.java': (r'(private Long userId;)', r'\1\n\n    @ManyToOne(fetch = FetchType.LAZY)\n    @JoinColumn(name = "user_id", insertable = false, updatable = false)\n    private User user;'),
    'WithdrawalRequest.java': (r'(private Long userId;)', r'\1\n\n    @ManyToOne(fetch = FetchType.LAZY)\n    @JoinColumn(name = "user_id", insertable = false, updatable = false)\n    private User user;')
}

for filename, (pattern, replacement) in replacements.items():
    filepath = os.path.join(entity_dir, filename)
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    new_content = re.sub(pattern, replacement, content)
    
    if new_content != content:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(new_content)
        print(f"Updated {filename}")
    else:
        print(f"No match found or already updated in {filename}")
