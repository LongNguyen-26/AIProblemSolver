import sys

def main():
    data = sys.stdin.read().strip()
    if not data:
        return
    # Allow leading zeros
    try:
        p = int(data)
    except ValueError:
        return
    items = [
        "Uphold integrity and ethics throughout the contest.",
        "Do not seek or receive external help from people, platforms, tools or AI.",
        "Follow all ICPC rules and guidelines, accept decisions made by organizers and judges as final.",
        "Show good sportmanship and treat competitors, volunteers, staff and judges with respect.",
        "Compete with creativity and teamwork, honor the contest spirit and pursue excellence."
    ]
    if 1 <= p <= 5:
        sys.stdout.write(items[p-1])

if __name__ == "__main__":
    main()
