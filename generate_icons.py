#!/usr/bin/env python3
"""
Generate fallback PNG icons for older Android versions from the vector drawable.
This creates simple icons with the app's purple background.
"""

from PIL import Image, ImageDraw
import os

# Icon sizes for different densities
sizes = {
    'mdpi': 48,
    'hdpi': 72,
    'xhdpi': 96,
    'xxhdpi': 144,
    'xxxhdpi': 192
}

# App's primary purple color
purple_color = (166, 77, 255)  # #A64DFF
white_color = (255, 255, 255)

def create_simple_timer_icon(size):
    """Create a simple timer icon with purple background"""
    # Create image with purple background
    img = Image.new('RGBA', (size, size), purple_color + (255,))
    draw = ImageDraw.Draw(img)
    
    # Calculate dimensions
    center = size // 2
    clock_radius = int(size * 0.3)
    
    # Draw white clock circle
    clock_left = center - clock_radius
    clock_top = center - clock_radius
    clock_right = center + clock_radius
    clock_bottom = center + clock_radius
    
    draw.ellipse([clock_left, clock_top, clock_right, clock_bottom], 
                fill=white_color, outline=white_color)
    
    # Draw clock hands
    hand_width = max(2, size // 24)
    
    # Hour hand (pointing to 12)
    hour_length = clock_radius * 0.5
    draw.line([center, center, center, center - hour_length], 
              fill=purple_color, width=hand_width)
    
    # Minute hand (pointing to 3)
    minute_length = clock_radius * 0.7
    draw.line([center, center, center + minute_length, center], 
              fill=purple_color, width=hand_width)
    
    # Center dot
    dot_radius = max(2, size // 16)
    draw.ellipse([center - dot_radius, center - dot_radius, 
                  center + dot_radius, center + dot_radius], 
                 fill=purple_color)
    
    return img

# Base directory for mipmap folders
base_dir = "/Users/surajiyer/Development/personal/buzztimer/app/src/main/res"

# Generate icons for each density
for density, size in sizes.items():
    # Create the directory if it doesn't exist
    mipmap_dir = os.path.join(base_dir, f"mipmap-{density}")
    os.makedirs(mipmap_dir, exist_ok=True)
    
    # Generate the icon
    icon = create_simple_timer_icon(size)
    
    # Save both regular and round versions
    icon_path = os.path.join(mipmap_dir, "ic_launcher.png")
    round_icon_path = os.path.join(mipmap_dir, "ic_launcher_round.png")
    
    icon.save(icon_path, "PNG")
    icon.save(round_icon_path, "PNG")  # Same icon for both in this simple case
    
    print(f"Generated {density} icons: {size}x{size} pixels")

print("Icon generation complete!")
print("All fallback PNG icons have been created for older Android versions.")
