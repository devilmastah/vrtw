import os

def generate_file_structure(root_dir):
    for root, dirs, files in os.walk(root_dir):
        # Skip .git directories
        if '.git' in dirs:
            dirs.remove('.git')
        
        level = root.replace(root_dir, '').count(os.sep)
        indent = ' ' * 4 * level
        print(f"{indent}[{os.path.basename(root)}]")  # Print the current directory
        subindent = ' ' * 4 * (level + 1)
        for file in files:
            # Skip .git files
            if not file.startswith('.git'):
                print(f"{subindent}{file}")  # Print files in the current directory

if __name__ == "__main__":
    # Replace this with the directory you want to crawl
    directory_to_scan = "."  # Current directory
    generate_file_structure(directory_to_scan)
