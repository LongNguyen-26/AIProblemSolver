import sys

def main():
    p_line = sys.stdin.readline().strip()
    if not p_line:
        return
    p = int(p_line)
    items = [
        "Uphold integrity and ethics throughout the contest.",
        "Do not seek or receive external help from people, platforms, tools or AI.",
        "Follow all ICPC rules and guidelines, accept decisions made by organizers and judges as final.",
        "Show good sportmanship and treat competitors, volunteers, staff and judges with respect.",
        "Compete with creativity and teamwork, honor the contest spirit and pursue excellence."
    ]
    # p is 1-indexed
    if 1 <= p <= 5:
        sys.stdout.write(items[p-1])
    else:
        # According to constraints this should never happen
        pass

if __name__ == "__main__":
    main()
