#include <bits/stdc++.h>
using namespace std;

struct Balloon {
    long long v; // current air
    long long t; // deadline
};

int main(){
    ios::sync_with_stdio(false);
    cin.tie(nullptr);
    int T; if(!(cin>>T)) return 0;
    while(T--){
        int n;cin>>n;
        vector<Balloon> a(n);
        long long maxT=0;
        for(int i=0;i<n;i++){
            cin>>a[i].t>>a[i].v;
            maxT=max(maxT,a[i].t);
        }
        // priority queue of (marginal gain, index)
        using Node=pair<long long,int>; // gain, idx
        auto cmp=[](const Node&x,const Node&y){return x.first<y.first;};
        priority_queue<Node,vector<Node>,decltype(cmp)> pq(cmp);
        // initially all balloons are available
        for(int i=0;i<n;i++){
            long long gain=2*a[i].v+1; // first liter marginal gain
            pq.emplace(gain,i);
        }
        long long totalBeauty=0;
        // simulate each second
        for(long long cur=0; cur<maxT; ++cur){
            // remove balloons whose deadline <= cur
            while(!pq.empty()){
                auto [gain, idx]=pq.top();
                if(a[idx].t<=cur){ // cannot use any more
                    pq.pop();
                    continue;
                }
                break;
            }
            if(pq.empty()) continue; // idle second
            auto [gain, idx]=pq.top(); pq.pop();
            // blow one liter into balloon idx
            a[idx].v+=1;
            // push updated marginal gain
            long long newGain=2*a[idx].v+1;
            pq.emplace(newGain,idx);
        }
        // compute final beauty
        for(int i=0;i<n;i++){
            totalBeauty+=a[i].v*a[i].v;
        }
        cout<<totalBeauty<<"\n";
    }
    return 0;
}
