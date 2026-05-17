#include <bits/stdc++.h>
using namespace std;

int main(){
    ios::sync_with_stdio(false);
    cin.tie(nullptr);
    int T; if(!(cin>>T)) return 0;
    while(T--){
        int n;cin>>n;
        vector<long long> t(n), v(n);
        long long maxT=0;
        for(int i=0;i<n;i++){
            cin>>t[i]>>v[i];
            maxT=max(maxT,t[i]);
        }
        // events: at each time we add balloons whose deadline > current time
        vector<vector<int>> add(maxT+2);
        for(int i=0;i<n;i++){
            // balloon i is available for times [0, t[i)-1]
            add[0].push_back(i);
        }
        // priority queue of pair(current amount, index) max by current amount
        struct Node{ long long cur; int idx;};
        struct Cmp{ bool operator()(const Node&a,const Node&b) const{ return a.cur<b.cur; } };
        priority_queue<Node, vector<Node>, Cmp> pq;
        // we will push all balloons at start, but need to remove when deadline passes.
        // To handle removal, store deadline and skip when popped if expired.
        vector<long long> cur(v.begin(), v.end());
        for(int i=0;i<n;i++) pq.push({cur[i], i});
        long long total=0;
        for(long long time=0; time<maxT; ++time){
            // pop expired balloons
            while(!pq.empty() && t[pq.top().idx]<=time) pq.pop();
            if(pq.empty()) continue;
            Node top=pq.top(); pq.pop();
            // blow one liter into this balloon
            cur[top.idx]++;
            pq.push({cur[top.idx], top.idx});
        }
        for(int i=0;i<n;i++) total+=cur[i]*cur[i];
        cout<<total<<"\n";
    }
    return 0;
}
