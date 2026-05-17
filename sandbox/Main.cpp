#include <bits/stdc++.h>
using namespace std;

int main(){
    ios::sync_with_stdio(false);
    cin.tie(nullptr);
    int T; if(!(cin>>T)) return 0;
    while(T--){
        int n;cin>>n;
        vector<long long> t(n), v(n);
        for(int i=0;i<n;i++) cin>>t[i]>>v[i];
        // sort indices by deadline
        vector<int> idx(n);
        iota(idx.begin(), idx.end(), 0);
        sort(idx.begin(), idx.end(), [&](int a,int b){return t[a]<t[b];});
        // max-heap of (current value, id)
        priority_queue<pair<long long,int>> pq;
        vector<char> expired(n,0);
        for(int i=0;i<n;i++) pq.emplace(v[i], i);
        long long cur=0;
        size_t pos=0; // next deadline index in idx
        while(!pq.empty()){
            // skip expired tops
            while(!pq.empty() && expired[pq.top().second]) pq.pop();
            if(pq.empty()) break;
            long long next_dead = (pos<idx.size()? t[idx[pos]] : LLONG_MAX);
            long long delta = next_dead - cur;
            if(delta<=0){
                // process all deadlines equal to cur
                while(pos<idx.size() && t[idx[pos]]==cur){
                    expired[idx[pos]]=1;
                    ++pos;
                }
                continue;
            }
            // allocate all delta to the current best balloon
            auto top = pq.top(); pq.pop();
            int id = top.second;
            v[id] += delta;
            cur = next_dead;
            // push back with updated value
            pq.emplace(v[id], id);
            // now remove all balloons whose deadline is cur
            while(pos<idx.size() && t[idx[pos]]==cur){
                expired[idx[pos]]=1;
                ++pos;
            }
        }
        long long ans=0;
        for(int i=0;i<n;i++) ans += v[i]*v[i];
        cout<<ans<<"\n";
    }
    return 0;
}
